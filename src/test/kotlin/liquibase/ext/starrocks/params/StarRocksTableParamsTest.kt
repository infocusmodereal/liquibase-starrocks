package liquibase.ext.starrocks.params

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StarRocksTableParamsTest {
    @Test
    fun `metadata tables retain primary keys and valid hash distribution syntax`() {
        val params = StarRocksTableParams().apply {
            engine = "OLAP"
            key_desc = "ID, AUTHOR, FILENAME"
            distributedBy = "HASH(ID) BUCKETS 1"
            properties = mapOf("replication_num" to "1")
        }
        assertEquals(
            " ENGINE = OLAP PRIMARY KEY (ID, AUTHOR, FILENAME)" +
                " DISTRIBUTED BY HASH(ID) BUCKETS 1 PROPERTIES (\"replication_num\"=\"1\")",
            params.generateSql()
        )
    }
}
