/*
 * This file is part of Crimson (https://github.com/Zohiu-Minecraft/Crimson).
 * Copyright (c) 2025 Zohiu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.zohiu.crimson

import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * The Crimson class provides a central management interface for the Crimson plugin.
 * This class manages plugin file directories, allows the creation of databases, effects, etc.
 * and handles the cleanup of resources when the plugin is disabled or reloaded.
 *
 * @property plugin The instance of the main JavaPlugin object representing the plugin.
 * @property configPath The path to the plugin's configuration directory.
 * @property dataPath The path to the plugin's data directory.
 * @property runningEffects A list of currently running effects that are active in the plugin.
 * @property databaseConnections A list of active database connections.
 */
class Crimson(val plugin: JavaPlugin) {
    // Directory paths for configuration and data storage
    private val pluginFolder = plugin.dataFolder
    val configPath = "${pluginFolder}${File.separator}config${File.separator}"
    val dataPath = "${pluginFolder}${File.separator}data${File.separator}"

    init {
        // Initialize directories if they do not exist
        File(configPath).mkdirs()
        File(dataPath).mkdirs()

        // Register GUI companion object
        GUI.crimson = this
        plugin.server.pluginManager.registerEvents(GUI, plugin)
    }

    /**
     * Creates and returns a new [Config] object for a given config file name.
     *
     * @param name The name of the config file to load.
     * @return The [Config] object for the specified file.
     */
    fun getConfig(name: String): Config {
        return Config(this, name)
    }

    /**
     * Creates and returns a new [Database] instance, which manages a connection to an SQLite database.
     * The database connection can be configured with caching settings.
     *
     * @param name The name of the database file (without extension).
     * @param cacheLevel The level of caching to be used for this database.
     * @param maxCacheSize The maximum number of records to cache in memory.
     * @param period The optional periodic interval for committing changes, only used with `CacheLevel.PERIODIC`.
     * @param periodCondition A condition that determines whether the periodic commit should occur.
     * @return The [Database] instance configured with the specified parameters.
     */
    fun getDatabase(
        name: String,
        cacheLevel: CacheLevel,
        maxCacheSize: Int = 500,
        period: Long? = null,
        periodCondition: () -> Boolean = { true }
    ): Database {
        return Database(this, name, cacheLevel, maxCacheSize, period, periodCondition)
    }

    /**
     * Creates and returns a new [Effect] instance, which is used to define and execute actions
     * that affect the game or plugin behavior over time.
     *
     * @return The newly created [Effect] instance.
     */
    fun effectBuilder(): Effect {
        return Effect(this, plugin)
    }

    /**
     * Cleans up and unregisters all active resources associated with the plugin.
     * This includes unregistering all events, stopping all running effects,
     * and closing all active database connections. This is typically called during plugin shutdown or reload.
     */
    fun cleanup() {
        // Unregister all events to allow plugin reloading
        HandlerList.unregisterAll(plugin)

        // Stop all running effects and close all database connections
        Effect.running.toMutableList().forEach { it.destroy() }
        Database.connections.toMutableList().forEach { it.close() }
    }
}
