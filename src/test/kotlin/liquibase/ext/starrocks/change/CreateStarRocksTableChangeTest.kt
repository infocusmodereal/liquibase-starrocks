package liquibase.ext.starrocks.change

import liquibase.change.ColumnConfig
import liquibase.change.ConstraintsConfig
import liquibase.ext.starrocks.database.StarRocksDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CreateStarRocksTableChangeTest {
    private fun change() = CreateStarRocksTableChange().apply {
        tableName = "orders"
        keyColumns = "id"
        distributionColumns = "id"
        addColumn(ColumnConfig().setName("id").setType("INT"))
        addColumn(ColumnConfig().setName("amount").setType("DECIMAL(12,2)"))
    }

    @Test
    fun `explicit layout generates native primary and duplicate table SQL`() {
        for (model in listOf("PRIMARY", "DUPLICATE")) {
            val change = change().apply { keyModel = model }
            assertFalse(change.validate(StarRocksDatabase()).hasErrors())
            val sql = change.generateStatements(StarRocksDatabase()).single().toString()
            assertTrue(sql.contains("$model KEY (id)"), sql)
            assertTrue(sql.contains("DISTRIBUTED BY HASH (id)"), sql)
        }
    }

    @Test
    fun `invalid layouts and ignored column options fail before executing`() {
        val cases = listOf(
            change().apply { distributionColumns = "missing" },
            change().apply { keyColumns = "amount" },
            change().apply { keyModel = "AGGREGATE" },
            change().apply { buckets = 0 },
            change().apply { replicationNum = 0 },
            change().apply { columns[0].constraints = ConstraintsConfig().setNullable(true) },
            change().apply { columns[1].defaultValue = "12" }
        )
        cases.forEach { assertTrue(it.validate(StarRocksDatabase()).hasErrors()) }
    }
}
