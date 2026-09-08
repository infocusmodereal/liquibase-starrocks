package liquibase.ext.starrocks.snapshot

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.DatabaseException
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.snapshot.DatabaseSnapshot
import liquibase.snapshot.jvm.PrimaryKeySnapshotGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.Column
import liquibase.structure.core.PrimaryKey
import liquibase.structure.core.Table

/** Connector/J exposes no primary keys for StarRocks; read the declared OLAP model. */
class StarRocksPrimaryKeySnapshotGenerator : PrimaryKeySnapshotGenerator() {
    override fun getPriority(type: Class<out DatabaseObject>, database: Database): Int =
        if (database !is StarRocksDatabase) PRIORITY_NONE
        else if (type == PrimaryKey::class.java) PRIORITY_DATABASE else super.getPriority(type, database)

    override fun replaces(): Array<Class<out liquibase.snapshot.SnapshotGenerator>> =
        arrayOf(PrimaryKeySnapshotGenerator::class.java)

    override fun snapshotObject(example: DatabaseObject, snapshot: DatabaseSnapshot): DatabaseObject? =
        readKey((example as PrimaryKey).table, snapshot)

    override fun addTo(foundObject: DatabaseObject, snapshot: DatabaseSnapshot) {
        if (foundObject is Table && snapshot.snapshotControl.shouldInclude(PrimaryKey::class.java)) {
            foundObject.primaryKey = readKey(foundObject, snapshot)
        }
    }

    private fun readKey(table: Table, snapshot: DatabaseSnapshot): PrimaryKey? {
        val database = snapshot.database
        val connection = database.connection as? JdbcConnection ?: return null
        val name = database.escapeTableName(table.schema?.catalogName, table.schema?.name, table.name)
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery("SHOW CREATE TABLE $name").use { result ->
                    if (!result.next()) return null
                    val ddl = result.getString(2)
                    val key = Regex("(?m)^PRIMARY KEY\\s*\\(([^\\n]+)\\)").find(ddl) ?: return null
                    val columns = Regex("`((?:``|[^`])+)`").findAll(key.groupValues[1]).map { it.groupValues[1].replace("``", "`") }.toList()
                    if (columns.isEmpty()) throw DatabaseException("Cannot parse StarRocks primary key for ${table.name}")
                    return PrimaryKey().setName("PRIMARY").setTable(table).apply {
                        columns.forEachIndexed { index, column -> addColumn(index, Column(column).setRelation(table)) }
                    }
                }
            }
        } catch (e: java.sql.SQLException) {
            throw DatabaseException("Cannot read StarRocks primary key for ${table.name}", e)
        }
    }
}
