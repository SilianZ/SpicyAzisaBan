package net.azisaba.spicyAzisaBan.migrations

import net.azisaba.spicyAzisaBan.SpicyAzisaBan
import util.promise.rewrite.Promise

interface DatabaseMigration {
    companion object {
        private val migrations = listOf<DatabaseMigration>()

        fun run(): Promise<Unit> = Promise.create { context ->
            val initialVersion = SpicyAzisaBan.instance.settings.getDatabaseVersion().complete()
            SpicyAzisaBan.instance.logger.info("Running database migrations (current database version: $initialVersion)")
            SpicyAzisaBan.instance.logger.info("${migrations.size} migrations loaded.")
            migrations.forEach { migration ->
                val version = SpicyAzisaBan.instance.settings.getDatabaseVersion().complete()
                if (migration.targetDatabaseVersion == version) {
                    SpicyAzisaBan.instance.logger.info("Migrating '${migration.name}' (database version $version)")
                    try {
                        migration.execute()
                    } catch (e: Throwable) {
                        SpicyAzisaBan.instance.logger.severe("Error migrating '${migration.name}' (database version $version)")
                        throw e
                    }
                    SpicyAzisaBan.instance.logger.info("Migrated '${migration.name}' (database version $version)")
                }
            }
            val version = SpicyAzisaBan.instance.settings.getDatabaseVersion().complete()
            SpicyAzisaBan.instance.logger.info("Completed database migrations (current database version: $version)")
            SpicyAzisaBan.instance.settings.setDatabaseVersion(version)
            context.resolve()
        }
    }

    val name: String
        get() = "Migration for database version $targetDatabaseVersion"
    val targetDatabaseVersion: Int

    fun execute()
}
