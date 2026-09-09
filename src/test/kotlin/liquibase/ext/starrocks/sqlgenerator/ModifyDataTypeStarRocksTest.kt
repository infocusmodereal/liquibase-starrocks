package liquibase.ext.starrocks.sqlgenerator

import liquibase.exception.UnexpectedLiquibaseException
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sqlgenerator.SqlGeneratorFactory
import liquibase.statement.core.ModifyDataTypeStatement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ModifyDataTypeStarRocksTest {
    @Test
    fun `unsupported type modification fails validation instead of silently succeeding`() {
        val statement = ModifyDataTypeStatement(null, null, "users", "name", "VARCHAR(512)")
        val factory = SqlGeneratorFactory.getInstance()
        val database = StarRocksDatabase()
        assertTrue(factory.validate(statement, database).hasErrors())
        assertThrows(UnexpectedLiquibaseException::class.java) { factory.generateSql(statement, database) }
    }
}
