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
import liquibase.sql.Sql
import liquibase.sqlgenerator.SqlGeneratorFactory
import liquibase.statement.core.RawSqlStatement

/**
 * Utility class for SQL generation in StarRocks
 */
object SqlGeneratorUtil {

    /**
     * Generates Sql objects from raw SQL statements
     *
     * @param database The database to generate SQL for
     * @param statements The raw SQL statements to convert
     * @return An array of Sql objects
     */
    fun generateSql(database: Database, vararg statements: String): Array<Sql> {
        val sqlGeneratorFactory = SqlGeneratorFactory.getInstance()
        val allSqlStatements = mutableListOf<Sql>()
        
        for (statement in statements) {
            val rawSqlStatement = RawSqlStatement(statement)
            val perStatement = sqlGeneratorFactory.generateSql(rawSqlStatement, database)
            allSqlStatements.addAll(perStatement)
        }
        
        return allSqlStatements.toTypedArray()
    }
}