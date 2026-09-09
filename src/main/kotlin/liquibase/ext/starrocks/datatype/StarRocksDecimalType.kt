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
import liquibase.datatype.core.DecimalType
import liquibase.ext.starrocks.database.StarRocksDatabase

/**
 * StarRocks DECIMAL type
 */
class StarRocksDecimalType : DecimalType() {

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun supports(database: Database): Boolean =
        database is StarRocksDatabase

    override fun toDatabaseDataType(database: Database): DatabaseDataType {
        if (database is StarRocksDatabase) {
            require(parameters.size <= 2) { "StarRocks DECIMAL accepts at most precision and scale" }
            val precision = parameters.getOrNull(0)?.toString()?.toIntOrNull() ?: if (parameters.isEmpty()) 10
                else throw IllegalArgumentException("StarRocks DECIMAL requires integer precision")
            val scale = parameters.getOrNull(1)?.toString()?.toIntOrNull() ?: if (parameters.size < 2) 0
                else throw IllegalArgumentException("StarRocks DECIMAL requires integer scale")
            require(precision in 1..38 && scale in 0..precision) {
                "StarRocks DECIMAL requires precision 1..38 and scale 0..precision"
            }
            return DatabaseDataType("DECIMAL($precision, $scale)")
        }
        return super.toDatabaseDataType(database)
    }
}
