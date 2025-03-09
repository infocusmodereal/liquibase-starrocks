# liquibase-starrocks

**Liquibase-StarRocks** is a plugin developed to integrate **Liquibase** schema management capabilities with **StarRocks**, 
a high-performance, real-time analytic database designed for sub-second queries at scale. This plugin allows developers and database administrators to automate and 
manage schema changes in StarRocks, ensuring version control and seamless database migrations.

## Project Structure

```
liquibase-starrocks/
├── build.gradle.kts           # Gradle build configuration
├── settings.gradle.kts        # Gradle settings
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── liquibase/
│   │   │       └── ext/
│   │   │           └── starrocks/
│   │   │               ├── database/       # Database connection and configuration
│   │   │               ├── lockservice/    # Lock service implementation
│   │   │               ├── params/         # Parameter handling
│   │   │               └── sqlgenerator/   # SQL generation
│   │   └── resources/
│   │       └── META-INF/
│   │           └── services/              # Service provider configuration files
│   └── test/
│       ├── kotlin/            # Test code
│       └── resources/         # Test resources
```

## Development

### Building the Project

```bash
./gradlew build shadowJar
```

### Running Tests

```bash
./gradlew test
```

## Testing with Liquibase CLI

To test this extension with Liquibase CLI, follow these steps:

1. **Download Liquibase CLI**:
   ```bash
   curl -L https://github.com/liquibase/liquibase/releases/download/v4.23.0/liquibase-4.23.0.zip -o liquibase.zip
   unzip liquibase.zip -d liquibase
   ```

2. **Build the extension with Shadow JAR**:
   ```bash
   ./gradlew clean shadowJar
   ```

   > **Note**: It's important to use the `shadowJar` task to create a fat JAR that includes all necessary dependencies, including the Kotlin runtime. This is required for the extension to work properly with Liquibase.

3. **Copy the extension JAR to Liquibase's lib directory**:
   ```bash
   cp build/libs/liquibase-starrocks-0.1.0.jar liquibase/lib/
   ```

4. **Copy the MySQL connector to Liquibase's lib directory**:
   ```bash
   # You can download it from Maven Central or use the one in your local Maven repository
   cp ~/.m2/repository/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar liquibase/lib/
   ```

5. **Create a liquibase.properties file**:
   ```properties
   # liquibase.properties
   url=jdbc:mysql://localhost:9030/your_database
   driver=com.mysql.cj.jdbc.Driver
   username=your_username
   password=your_password
   changeLogFile=changelog.yaml
   ```

6. **Create a changelog file (changelog.yaml)**:
   ```yaml
   databaseChangeLog:
     - changeSet:
         id: 1
         author: liquibase
         changes:
           - sql:
               sql: |
                 CREATE TABLE test_table (
                   id INT NOT NULL,
                   name VARCHAR(255),
                   UNIQUE KEY(id)
                 ) ENGINE = OLAP
                 DISTRIBUTED BY HASH(id) BUCKETS 10
                 PROPERTIES ('replication_num' = '1');
   ```

   > **Note**: StarRocks has specific syntax for creating tables:
   > - Use `UNIQUE KEY(column1, column2, ...)` after the column definitions to create a primary key, not within the column definitions
   > - Use `DISTRIBUTED BY HASH(column)` to specify the distribution key
   > - Set appropriate `BUCKETS` and `replication_num` values based on your cluster configuration
   > - StarRocks only supports UPDATE operations on tables with a UNIQUE KEY defined

7. **Run Liquibase update**:
   ```bash
   ./liquibase/liquibase update
   ```

8. **Verify the changes in StarRocks**:
   ```sql
   -- Connect to your StarRocks database and run:
   SHOW TABLES;
   DESCRIBE test_table;
   ```
## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
