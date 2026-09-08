package liquibase.ext.starrocks.configuration

import liquibase.configuration.AutoloadedConfigurations
import liquibase.configuration.ConfigurationDefinition

class StarRocksConfiguration : AutoloadedConfigurations {
    companion object {
        val METADATA_REPLICATION: ConfigurationDefinition<Int> = ConfigurationDefinition.Builder("liquibase.starrocks")
            .define("metadataReplication", Int::class.javaObjectType)
            .setDescription("Replication count for newly created Liquibase metadata tables; existing tables are unchanged")
            .setDefaultValue(1)
            .build()

        fun metadataReplication(): String {
            val value = METADATA_REPLICATION.currentValue
            require(value > 0) { "liquibase.starrocks.metadataReplication must be positive" }
            return value.toString()
        }
    }
}
