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
package liquibase.ext.starrocks.database

import liquibase.database.AbstractJdbcDatabase
import liquibase.database.DatabaseConnection

/**
 * StarRocks Database implementation for Liquibase
 */
class StarRocksDatabase : AbstractJdbcDatabase() {

    companion object {
        private const val DATABASE_NAME = "StarRocks"
        private const val DEFAULT_PORT = 9030 // Default StarRocks port
        private const val DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver" // Using MySQL driver as StarRocks is MySQL-compatible

        // Current date time function for StarRocks
        val CURRENT_DATE_TIME_FUNCTION = "NOW()"
    }

    init {
        this.currentDateTimeFunction = CURRENT_DATE_TIME_FUNCTION
    }

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun getDefaultDatabaseProductName(): String = DATABASE_NAME

    override fun isCorrectDatabaseImplementation(conn: DatabaseConnection): Boolean {
        // Check if the database product name is StarRocks
        if (DATABASE_NAME.equals(conn.databaseProductName, ignoreCase = true)) {
            return true
        }

        // If using MySQL driver, check the URL for StarRocks indicators
        val url = conn.url
        if (url != null && url.contains("starrocks")) {
            return true
        }

        // Try to detect StarRocks by checking version information
        try {
            val versionInfo = conn.getDatabaseProductVersion()
            if (versionInfo != null && versionInfo.contains("StarRocks", ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {
            // Ignore exceptions when checking version
        }

        return false
    }

    override fun getDefaultDriver(url: String?): String? =
        if (url != null && (url.startsWith("jdbc:starrocks") || url.startsWith("jdbc:mysql"))) DRIVER_CLASS_NAME else null

    override fun getShortName(): String = "starrocks"

    override fun getDefaultPort(): Int = DEFAULT_PORT

    override fun supportsInitiallyDeferrableColumns(): Boolean = false

    override fun supportsTablespaces(): Boolean = false

    override fun supportsSequences(): Boolean = false

    override fun supportsSchemas(): Boolean = false

    override fun supportsDDLInTransaction(): Boolean = false
}
