package liquibase.ext.starrocks.sqlgenerator

import liquibase.database.Database
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sqlgenerator.core.AddColumnGenerator
import liquibase.statement.core.AddColumnStatement

/** Keep core type/default/constraint handling, using StarRocks's required COLUMN keyword. */
class AddColumnStarRocks : AddColumnGenerator() {
    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: AddColumnStatement, database: Database): Boolean = database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: AddColumnStatement,
        database: Database,
        chain: liquibase.sqlgenerator.SqlGeneratorChain<AddColumnStatement>
    ): Array<liquibase.sql.Sql> = super.generateSql(statement, database, chain)

    override fun generateSingleColumnSQL(statement: AddColumnStatement, database: Database): String {
        val clause = super.generateSingleColumnSQL(statement, database)
        if (!clause.startsWith(" ADD ")) throw UnexpectedLiquibaseException("Unexpected Liquibase ADD COLUMN clause")
        return " ADD COLUMN " + clause.removePrefix(" ADD ")
    }
}
