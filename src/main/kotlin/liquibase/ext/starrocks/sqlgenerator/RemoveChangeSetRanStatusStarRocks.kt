/*-
 * #%L
 * Liquibase extension for StarRocks
 * %%
 * Copyright (C) 2023 - 2024
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package liquibase.ext.starrocks.sqlgenerator

import liquibase.changelog.ChangeSet
import liquibase.database.Database
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sql.UnparsedSql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.RemoveChangeSetRanStatusGenerator
import liquibase.statement.core.RemoveChangeSetRanStatusStatement

/**
 * StarRocks Remove Change Set Ran Status Generator
 */
class RemoveChangeSetRanStatusStarRocks : RemoveChangeSetRanStatusGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: RemoveChangeSetRanStatusStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: RemoveChangeSetRanStatusStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<RemoveChangeSetRanStatusStatement>
    ): Array<Sql> {
        val tableName = database.databaseChangeLogTableName
        val schemaName = database.defaultSchemaName
        val changeSet: ChangeSet = statement.changeSet

        // Use standard DELETE FROM syntax for StarRocks
        val deleteQuery = """
            DELETE FROM `$schemaName`.$tableName 
            WHERE ID = '${changeSet.id}' 
            AND AUTHOR = '${changeSet.author}' 
            AND FILENAME = '${changeSet.filePath}'
        """.trimIndent()

        return arrayOf(UnparsedSql(deleteQuery))
    }
}
