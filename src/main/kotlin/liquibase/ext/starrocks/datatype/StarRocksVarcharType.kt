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
package liquibase.ext.starrocks.datatype

import liquibase.database.Database
import liquibase.datatype.DatabaseDataType
import liquibase.datatype.core.VarcharType
import liquibase.ext.starrocks.database.StarRocksDatabase

/**
 * StarRocks VARCHAR type
 */
class StarRocksVarcharType : VarcharType() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(database: Database): Boolean =
        database is StarRocksDatabase

    override fun toDatabaseDataType(database: Database): DatabaseDataType {
        if (database is StarRocksDatabase) {
            require(parameters.size <= 1) { "StarRocks VARCHAR accepts at most one length parameter" }
            val size = parameters.getOrNull(0)?.toString()?.toIntOrNull() ?: if (parameters.isEmpty()) 255
                else throw IllegalArgumentException("StarRocks VARCHAR requires an integer length")
            require(size in 1..1048576) { "StarRocks VARCHAR length must be between 1 and 1048576" }
            return DatabaseDataType("VARCHAR($size)")
        }
        return super.toDatabaseDataType(database)
    }
}
