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

import liquibase.database.Database
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.ModifyDataTypeGenerator
import liquibase.statement.core.ModifyDataTypeStatement

/**
 * No-op implementation to prevent Liquibase from trying to modify the changelog table column sizes.
 * StarRocks uses VARCHAR type for strings, not CHAR(N) or similar fixed-length types.
 */
class ModifyDataTypeStarRocks : ModifyDataTypeGenerator() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: ModifyDataTypeStatement, database: Database): Boolean =
        database is StarRocksDatabase

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: ModifyDataTypeStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<ModifyDataTypeStatement>
    ): Array<Sql> {
        // Return empty array to prevent any SQL from being executed
        return emptyArray()
    }
}
