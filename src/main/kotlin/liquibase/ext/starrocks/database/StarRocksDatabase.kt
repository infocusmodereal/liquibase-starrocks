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

    private var serverVersion: String? = null

    override fun setConnection(conn: DatabaseConnection) {
        serverVersion = null
        super.setConnection(conn)
    }

    override fun getDatabaseProductVersion(): String {
        serverVersion?.let { return it }
        val jdbc = connection as? liquibase.database.jvm.JdbcConnection ?: return super.getDatabaseProductVersion()
        try {
            jdbc.createStatement().use { statement ->
                statement.queryTimeout = 5
                statement.executeQuery("SELECT current_version()").use { result ->
                    if (!result.next()) throw liquibase.exception.DatabaseException("StarRocks returned no server version")
                    return result.getString(1).also { serverVersion = it }
                }
            }
        } catch (e: java.sql.SQLException) {
            throw liquibase.exception.DatabaseException("Cannot read StarRocks server version", e)
        }
    }

    override fun getDatabaseMajorVersion(): Int = databaseProductVersion.substringBefore('.').toInt()

    override fun getDatabaseMinorVersion(): Int = databaseProductVersion.split('.')[1].toInt()

    override fun getPriority(): Int = PRIORITY_DATABASE

    override fun getDefaultDatabaseProductName(): String = DATABASE_NAME

    override fun isCorrectDatabaseImplementation(conn: DatabaseConnection): Boolean {
        // Check if the database product name is StarRocks
        if (DATABASE_NAME.equals(conn.databaseProductName, ignoreCase = true)) {
            return true
        }

        if (conn.databaseProductVersion?.contains("StarRocks", ignoreCase = true) == true) {
            return true
        }
        // MySQL's protocol/version string does not identify StarRocks. Probe its
        // server-specific function instead of relying on host names or ports.
        if (conn is liquibase.database.jvm.JdbcConnection) {
            try {
                conn.createStatement().use { statement ->
                    statement.queryTimeout = 5
                    statement.executeQuery("SELECT current_version()").use { result ->
                        return result.next() && result.getString(1)
                            .matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+.*"))
                    }
                }
            } catch (e: java.sql.SQLException) {
                liquibase.Scope.getCurrentScope().getLog(javaClass)
                    .fine("StarRocks version probe unavailable; use explicit databaseClass for ambiguous connections.")
            }
        }

        return false
    }

    override fun getDefaultDriver(url: String?): String? =
        if (url != null && url.startsWith("jdbc:mysql:")) DRIVER_CLASS_NAME else null

    override fun getShortName(): String = "starrocks"

    override fun getDefaultPort(): Int = DEFAULT_PORT

    override fun getQuotingStartCharacter(): String = "`"

    override fun getQuotingEndCharacter(): String = "`"

    override fun getQuotingEndReplacement(): String = "``"

    override fun supportsCatalogs(): Boolean = true

    override fun supports(type: Class<out liquibase.structure.DatabaseObject>): Boolean =
        when (type) {
            liquibase.structure.core.ForeignKey::class.java,
            liquibase.structure.core.UniqueConstraint::class.java,
            liquibase.structure.core.Index::class.java -> false
            else -> super.supports(type)
        }

    override fun isLiquibaseObject(obj: liquibase.structure.DatabaseObject): Boolean {
        if (obj is liquibase.structure.core.View) {
            val expected = liquibase.structure.core.View()
                .setName(databaseChangeLogLockTableName + "_MUTEX")
                .setSchema(liquibase.structure.core.Schema(liquibaseCatalogName, liquibaseSchemaName))
            return liquibase.diff.compare.DatabaseObjectComparatorFactory.getInstance()
                .isSameObject(obj, expected, null, this)
        }
        return super.isLiquibaseObject(obj)
    }

    override fun supportsAutoIncrement(): Boolean = false

    override fun supportsInitiallyDeferrableColumns(): Boolean = false

    override fun supportsTablespaces(): Boolean = false

    override fun supportsSequences(): Boolean = false

    override fun supportsSchemas(): Boolean = false

    override fun supportsDDLInTransaction(): Boolean = false
}
