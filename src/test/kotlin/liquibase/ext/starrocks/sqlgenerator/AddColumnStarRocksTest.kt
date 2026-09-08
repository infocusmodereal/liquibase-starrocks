package liquibase.ext.starrocks.sqlgenerator

import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sqlgenerator.SqlGeneratorFactory
import liquibase.statement.core.AddColumnStatement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AddColumnStarRocksTest {
    @Test
    fun `core addColumn emits the required COLUMN keyword without losing the type`() {
        val db = StarRocksDatabase()
        val statement = AddColumnStatement("app", null, "events", "extra", "VARCHAR(32)", null)
        val sql = SqlGeneratorFactory.getInstance().generateSql(statement, db).single().toSql()
        assertEquals("ALTER TABLE app.events ADD COLUMN extra VARCHAR(32)", sql)
    }
}
