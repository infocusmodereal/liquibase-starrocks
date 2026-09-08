package liquibase.ext.starrocks

import liquibase.database.DatabaseConnection
import liquibase.database.DatabaseFactory
import liquibase.datatype.DataTypeFactory
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.ext.starrocks.lockservice.StarRocksLockService
import liquibase.lockservice.LockServiceFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PluginDiscoveryTest {
    @Test
    fun `service loader discovers StarRocks database and lock service`() {
        val connection = mock(DatabaseConnection::class.java)
        `when`(connection.databaseProductName).thenReturn("StarRocks")
        `when`(connection.url).thenReturn("jdbc:mysql://localhost:9030/liquibase_test")
        val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(connection)
        assertInstanceOf(StarRocksDatabase::class.java, database)
        assertInstanceOf(StarRocksLockService::class.java, LockServiceFactory.getInstance().getLockService(database))
    }

    @Test
    fun `registered type mappings retain StarRocks SQL across runtimes`() {
        val database = StarRocksDatabase()
        val expectedTypes = mapOf(
            "int" to "INT", "datetime" to "DATETIME",
            "varchar(42)" to "VARCHAR(42)", "decimal(12,2)" to "DECIMAL(12, 2)"
        )
        expectedTypes.forEach { (description, expected) ->
            val type = DataTypeFactory.getInstance().fromDescription(description, database)
            assertEquals(expected, type.toDatabaseDataType(database).toString(), description)
        }
    }
}
