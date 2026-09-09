package liquibase.ext.starrocks.change

import liquibase.change.ChangeMetaData
import liquibase.change.DatabaseChange
import liquibase.change.DatabaseChangeProperty
import liquibase.change.core.CreateTableChange
import liquibase.database.Database
import liquibase.datatype.DataTypeFactory
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.exception.ValidationErrors
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.statement.SqlStatement
import liquibase.statement.core.RawSqlStatement

/** Explicit OLAP layout; inherited columns and drop-table inverse use core Liquibase APIs. */
@DatabaseChange(name = "createStarRocksTable", description = "Creates an OLAP table with an explicit key and distribution", priority = ChangeMetaData.PRIORITY_DEFAULT)
class CreateStarRocksTableChange : CreateTableChange() {
    @get:DatabaseChangeProperty(description = "PRIMARY or DUPLICATE")
    var keyModel: String = "PRIMARY"

    @get:DatabaseChangeProperty(description = "Comma-separated key column names, in table prefix order", requiredForDatabase = ["starrocks"])
    var keyColumns: String? = null

    @get:DatabaseChangeProperty(description = "Comma-separated hash distribution column names", requiredForDatabase = ["starrocks"])
    var distributionColumns: String? = null

    @get:DatabaseChangeProperty(description = "Positive bucket count")
    var buckets: Int = 1

    @get:DatabaseChangeProperty(description = "Positive table replication count")
    var replicationNum: Int = 1

    private fun names(value: String?): List<String> = value?.split(',')?.map { it.trim() } ?: emptyList()

    // These optional core properties were introduced after the oldest supported runtime.
    private fun optionalFlag(getter: String): Boolean =
        javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }?.invoke(this) == true

    override fun validate(database: Database): ValidationErrors {
        val errors = ValidationErrors()
        if (database !is StarRocksDatabase) errors.addError("createStarRocksTable requires StarRocks")
        errors.checkRequiredField("tableName", tableName)
        if (columns.isEmpty()) errors.addError("At least one column is required")
        if (keyModel !in listOf("PRIMARY", "DUPLICATE")) errors.addError("keyModel must be PRIMARY or DUPLICATE")
        val keys = names(keyColumns)
        val distribution = names(distributionColumns)
        val columnNames = columns.map { it.name }
        if (keys.isEmpty() || keys.any { it.isBlank() } || keys.distinct().size != keys.size || columnNames.take(keys.size) != keys) {
            errors.addError("keyColumns must be a non-empty, unique prefix of the table columns")
        }
        if (distribution.isEmpty() || distribution.any { it !in columnNames } || distribution.distinct().size != distribution.size) {
            errors.addError("distributionColumns must name existing, unique columns")
        }
        if (keyModel == "PRIMARY" && distribution.any { it !in keys }) errors.addError("PRIMARY distribution columns must belong to the key")
        if (buckets < 1 || replicationNum < 1) errors.addError("buckets and replicationNum must be positive")
        if (columnNames.distinct().size != columns.size) errors.addError("Column names must be unique")
        if (remarks != null || tablespace != null || tableType != null || optionalFlag("getIfNotExists") || optionalFlag("getRowDependencies")) {
            errors.addError("remarks, tablespace, tableType, ifNotExists and rowDependencies are not supported; use explicit SQL")
        }
        columns.forEach { column ->
            errors.checkRequiredField("column.name", column.name)
            errors.checkRequiredField("column.type", column.type)
            for (field in column.serializableFields - setOf("name", "type", "constraints")) {
                val value = column.getSerializableFieldValue(field)
                if (value != null && value != false) errors.addError("Unsupported column option: $field; use explicit SQL")
            }
            column.constraints?.let { constraints ->
                for (field in constraints.serializableFields - setOf("nullable")) {
                    val value = constraints.getSerializableFieldValue(field)
                    if (value != null && value != false) errors.addError("Unsupported constraint: $field; use keyColumns or explicit SQL")
                }
            }
            if (keyModel == "PRIMARY" && column.name in keys && column.constraints?.isNullable == true) {
                errors.addError("PRIMARY key columns cannot be nullable")
            }
        }
        return errors
    }

    override fun generateStatements(database: Database): Array<SqlStatement> {
        val errors = validate(database)
        if (errors.hasErrors()) throw UnexpectedLiquibaseException(errors.toString())
        val keys = names(keyColumns)
        fun quoted(name: String): String = database.escapeColumnName(catalogName, schemaName, tableName, name)
        val definitions = columns.joinToString(", ") { column ->
            val type = DataTypeFactory.getInstance().fromDescription(column.type, database).toDatabaseDataType(database)
            val required = (keyModel == "PRIMARY" && column.name in keys) || column.constraints?.isNullable == false
            "${quoted(column.name)} $type" + if (required) " NOT NULL" else " NULL"
        }
        val table = database.escapeTableName(catalogName, schemaName, tableName)
        return arrayOf(RawSqlStatement("CREATE TABLE $table ($definitions) ENGINE = OLAP " +
            "$keyModel KEY (${keys.joinToString(", ") { quoted(it) }}) " +
            "DISTRIBUTED BY HASH (${names(distributionColumns).joinToString(", ") { quoted(it) }}) BUCKETS $buckets " +
            "PROPERTIES (\"replication_num\"=\"$replicationNum\")"))
    }

    override fun getConfirmationMessage(): String = "StarRocks table $tableName created"
}
