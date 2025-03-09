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
package liquibase.ext.starrocks.params

/**
 * StarRocks table parameters
 */
class StarRocksTableParams {
    var engine: String? = null
    var key_desc: String? = null
    var distributedBy: String? = null
    var properties: Map<String, String> = emptyMap()

    /**
     * Generate the SQL for the table parameters
     */
    fun generateSql(): String {
        val sql = StringBuilder()

        engine?.let { sql.append(" ENGINE = $it") }

        key_desc?.let { sql.append(" PRIMARY KEY ($it)") }
        
        if (properties.isNotEmpty()) {
            sql.append(" PROPERTIES (")
            sql.append(properties.entries.joinToString(", ") { (key, value) -> "\"$key\"=\"$value\"" })
            sql.append(")")
        }

        return sql.toString()
    }
}