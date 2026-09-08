package liquibase.ext.starrocks.sqlgenerator

import liquibase.ChecksumVersion
import liquibase.change.core.RawSQLChange
import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sqlgenerator.SqlGeneratorFactory
import liquibase.statement.core.UpdateChangeSetChecksumStatement
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateChangeSetChecksumStarRocksTest {
    @Test
    fun `checksum update uses calculated checksum and escapes the stored identity`() {
        val database = StarRocksDatabase().apply { defaultSchemaName = "liquibase_test" }
        val changeSet = ChangeSet("id'1", "author'1", false, false, "renamed.yaml", null, null, DatabaseChangeLog())
        changeSet.storedFilePath = "original'file.yaml"
        changeSet.addChange(RawSQLChange().apply { sql = "SELECT 1" })
        val expected = changeSet.generateCheckSum(ChecksumVersion.latest()).toString()
        val sql = SqlGeneratorFactory.getInstance()
            .generateSql(UpdateChangeSetChecksumStatement(changeSet), database).single().toSql()
        assertTrue(sql.contains("SET MD5SUM = '$expected'"), sql)
        assertTrue(sql.contains("ID = 'id''1'"), sql)
        assertTrue(sql.contains("AUTHOR = 'author''1'"), sql)
        assertTrue(sql.contains("FILENAME = 'original''file.yaml'"), sql)
    }
}
