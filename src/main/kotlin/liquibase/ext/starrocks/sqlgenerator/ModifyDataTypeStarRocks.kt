/*-
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
 */
package liquibase.ext.starrocks.sqlgenerator

import liquibase.database.Database
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.exception.ValidationErrors
import liquibase.ext.starrocks.database.StarRocksDatabase
import liquibase.sql.Sql
import liquibase.sqlgenerator.SqlGeneratorChain
import liquibase.sqlgenerator.core.ModifyDataTypeGenerator
import liquibase.statement.core.ModifyDataTypeStatement

/** Reject changes whose asynchronous completion and column attributes cannot be preserved. */
class ModifyDataTypeStarRocks : ModifyDataTypeGenerator() {
    companion object {
        const val MESSAGE = "StarRocks modifyDataType is not supported. Use an explicit SQL changeset " +
            "that preserves column attributes and verify SHOW ALTER TABLE completion before continuing."
    }

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(statement: ModifyDataTypeStatement, database: Database): Boolean =
        database is StarRocksDatabase

    override fun validate(
        statement: ModifyDataTypeStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<*>
    ): ValidationErrors = super.validate(statement, database, sqlGeneratorChain).addError(MESSAGE)

    @Suppress("ACCIDENTAL_OVERRIDE")
    override fun generateSql(
        statement: ModifyDataTypeStatement,
        database: Database,
        sqlGeneratorChain: SqlGeneratorChain<ModifyDataTypeStatement>
    ): Array<Sql> = throw UnexpectedLiquibaseException(MESSAGE)
}
