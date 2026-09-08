package liquibase.ext.starrocks.configuration

import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sqlgenerator.SqlGeneratorFactory
import liquibase.statement.core.CreateDatabaseChangeLogTableStatement
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StarRocksConfigurationTest {
    @Test
    fun `metadata replication is configurable and rejects non-positive counts`() {
        val key = "liquibase.starrocks.metadataReplication"
        val previous = System.getProperty(key)
        try {
            System.setProperty(key, "3")
            val sql = SqlGeneratorFactory.getInstance().generateSql(CreateDatabaseChangeLogTableStatement(), StarRocksDatabase()).single().toSql()
            assertTrue(sql.contains("\"replication_num\"=\"3\""), sql)
            System.setProperty(key, "0")
            assertThrows(IllegalArgumentException::class.java) { StarRocksConfiguration.metadataReplication() }
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }
}
