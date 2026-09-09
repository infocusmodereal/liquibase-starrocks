package liquibase.ext.starrocks.database

import liquibase.database.DatabaseConnection
import liquibase.datatype.DataTypeFactory
import liquibase.sqlgenerator.SqlGeneratorFactory
import liquibase.statement.core.InitializeDatabaseChangeLogLockTableStatement
import liquibase.structure.core.Table
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class StarRocksDatabaseTest {
    @Test
    fun `MySQL URLs containing misleading database names and ports are not claimed`() {
        for (url in listOf("jdbc:mysql://localhost:9030/mysql", "jdbc:mysql://starrocks-proxy:3306/starrocks", "jdbc:mysql://host:19030/db")) {
            val conn = mock(DatabaseConnection::class.java)
            `when`(conn.databaseProductName).thenReturn("MySQL")
            `when`(conn.databaseProductVersion).thenReturn("8.0.33")
            `when`(conn.url).thenReturn(url)
            assertFalse(StarRocksDatabase().isCorrectDatabaseImplementation(conn), url)
        }
    }

    @Test
    fun `metadata names use effective catalog and escape backticks`() {
        val db = StarRocksDatabase().apply {
            defaultCatalogName = "application"
            liquibaseCatalogName = "meta-db"
            databaseChangeLogLockTableName = "lock`table"
        }
        val sql = SqlGeneratorFactory.getInstance().generateSql(InitializeDatabaseChangeLogLockTableStatement(), db)
            .single().toSql()
        assertTrue(sql.contains("`meta-db`.`lock``table`"), sql)
        assertFalse(sql.contains("DELETE"), sql)
        assertTrue(sql.contains("WHERE NOT EXISTS"), sql)
        assertEquals("`select`", db.quoteObject("select", Table::class.java))
    }

    @Test
    fun `types accept default parameters and reject invalid boundaries`() {
        val db = StarRocksDatabase()
        val factory = DataTypeFactory.getInstance()
        for ((type, sql) in mapOf("varchar" to "VARCHAR(255)", "decimal" to "DECIMAL(10, 0)", "decimal(12)" to "DECIMAL(12, 0)")) {
            assertEquals(sql, factory.fromDescription(type, db).toDatabaseDataType(db).toString())
        }
        for (type in listOf("varchar(0)", "varchar(1048577)", "decimal(39,0)", "decimal(4,5)", "decimal(12,2,7)", "varchar(32,7)")) {
            assertThrows(IllegalArgumentException::class.java) { factory.fromDescription(type, db).toDatabaseDataType(db) }
        }
    }
}
