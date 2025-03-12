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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.*
import java.io.Serializable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException


/**
 * Enum class representing the different cache levels for the database.
 *
 * The cache level determines how the database should handle caching. The possible cache levels are:
 *
 * - **GET**: Only cache reads with a specified maximum.
 * - **PERIODIC**: Cache writes periodically, with a specified period and a condition to trigger cache commits.
 * - **FULL**: Cache both read and write operations.
 */
enum class CacheLevel {
    NONE, GET, WRITE_PERIODIC, FULL
}


/**
 * Exception thrown when a database-related operation fails in Crimson.
 *
 * @param message the error message that describes the exception
 */
class CrimsonDatabaseException(message: String) : RuntimeException(message)


/**
 * Represents a connection to a SQLite database with caching and commit functionality.
 *
 * The Database class provides methods to interact with a database, such as querying tables,
 * setting and getting values, and managing caching behaviors for performance optimization.
 *
 * @property crimson the Crimson instance associated with this database
 * @property name the name of the database file (without extension)
 * @property cacheLevel the cache level for the database (NONE, GET, WRITE_PERIODIC, FULL)
 * @property maxCacheSize the maximum size for the get cache. -1 to allow infinite size - be careful about memory usage.
 * @property period a period in milliseconds for periodic cache commits (used with WRITE_PERIODIC)
 * @property periodCondition a condition function that determines when to commit the cache (used with WRITE_PERIODIC)
 */
class Database(internal val crimson: Crimson, val name: String, cacheLevel: CacheLevel, private val maxCacheSize: Int,
               period: Long?, private val periodCondition: () -> Boolean) : AutoCloseable {
    companion object {
        val connections: MutableList<Database> = mutableListOf()
    }

    /** The SQLite connection to the database */
    internal var connection: Connection
    /** The cache for GET operations, storing key-value pairs for quick access */
    internal val getCache: HashMap<String, LinkedHashMap<String, Any>> = HashMap()
    /** The cache for write operations, storing prepared values */
    internal val writeCache: HashMap<String, HashMap<String, Array<String>>> = HashMap()

    /** Flags to indicate whether GET and WRITE operations are cached */
    internal var getCached = false
    internal var writeCached = false

    /** An optional periodic effect used with WRITE_PERIODIC */
    private var periodicEffect: Effect? = null

    /** Coroutine scope for asynchronous operations */
    internal val coroutineScope: CoroutineScope = MainScope()

    /** JSON object mapper for serializing and deserializing objects */
    internal val mapper = jacksonObjectMapper()

    init {
        val url = "jdbc:sqlite:${crimson.dataPath}${name}.db"
        try {
            connection = DriverManager.getConnection(url)
            connection.autoCommit = false
            connections.add(this)
        } catch (e: SQLException) {
            if (e.message != null) throw CrimsonDatabaseException(e.message!!)
            else throw e
        }

        mapper.registerKotlinModule()

        when(cacheLevel) {
            CacheLevel.NONE -> {}
            CacheLevel.GET -> {
                getCached = true
            }
            CacheLevel.WRITE_PERIODIC -> {
                writeCached = true
                getCached = true
                if (period == null) throw CrimsonDatabaseException("No period specified.")
                periodicEffect = crimson.effectBuilder().repeatForever(period) {
                    if (periodCondition.invoke()) asyncCommitCache()
                }.start()
            }
            CacheLevel.FULL -> {
                writeCached = true
                getCached = true
            }
        }
    }

    /**
     * Closes the database connection and commits any pending changes.
     *
     * This method also clears the caches and cancels the coroutine scope.
     */
    override fun close() {
        periodicEffect?.destroy()
        commitCache()
        connection.close()
        getCache.clear()
        coroutineScope.cancel()
        connections.remove(this)
    }

    /**
     * Asynchronously commits the write cache.
     */
    fun asyncCommitCache() {
        coroutineScope.launch(Dispatchers.IO) {
            commitCache()
        }
    }

    /**
     * Commits the write cache to the database.
     */
    fun commitCache() {
        if (!writeCached || writeCache.size == 0) return
        writeCache.keys.forEach { table ->
            writeCache[table]!!.keys.forEach { key ->
                val statement: PreparedStatement = connection.prepareStatement(
                    "INSERT OR REPLACE INTO $table (key, type, value) VALUES (?,?,?)"
                )

                statement.setString(1, key)
                statement.setString(2, writeCache[table]!![key]!![0])
                statement.setString(3, writeCache[table]!![key]!![1])
                statement.execute()
                statement.close()
            }
            writeCache[table] = HashMap()
        }
        connection.commit()
    }

    /**
     * Commits a single prepared statement to the database and commits the transaction.
     *
     * @param statement the prepared statement to execute
     */
    internal fun commit(statement: PreparedStatement) {
        try {
            statement.execute()
            statement.close()
            connection.commit()
        } catch (e: Exception) {
            if (e.message != null) throw CrimsonDatabaseException(e.message!!)
            else throw e
        }
    }

    /**
     * Retrieves or creates a table in the database.
     *
     * If the table does not exist, it will be created. Caches are also initialized if enabled.
     *
     * @param name the name of the table
     * @return a Table object for interacting with the database table
     */
    fun getTable(name: String) : Table {
        // Underscore before name to allow table names to start with numbers
        val internalName = "_$name"
        val statement: PreparedStatement = connection.prepareStatement(
            "CREATE TABLE IF NOT EXISTS $internalName (key STRING NOT NULL PRIMARY KEY, type STRING, value STRING)"
        )
        commit(statement)

        // Create linked hash map for table get cache
        if (getCached && !getCache.contains(internalName)) {
            if (maxCacheSize < 0) getCache[internalName] = LinkedHashMap()
            else {
                getCache[internalName] = object : LinkedHashMap<String, Any>() {
                    // Set correct max cache size
                    override fun removeEldestEntry(eldest: Map.Entry<String, Any>?): Boolean {
                        return size > maxCacheSize
                    }
                }
            }
        }

        // Create mutable list for table write cache
        if (writeCached && !writeCache.contains(internalName)) {
            writeCache[internalName] = HashMap()
        }

        return Table(this, internalName)
    }
}


/**
 * Represents a table in the database.
 *
 * @property database the parent Database object
 * @property internalName the internal name of the table (prefixed with an underscore)
 */
class Table(val database: Database, private val internalName: String) {
    /**
     * Sets a key-value pair in the table.
     *
     * The value is serialized and stored in the database. The operation is performed asynchronously.
     *
     * @param key the key to store the value under
     * @param value the value to be stored
     */
    fun <T : Serializable> set(key: String, value: T) {
        if (database.getCached) {
            database.getCache[internalName]!![key] = value
        }

        database.coroutineScope.launch(Dispatchers.IO) {
            try {
                val type = value.javaClass.name
                val serializedValue = database.mapper.writeValueAsString(value)

                if (database.writeCached) {
                    database.writeCache[internalName]!![key] = arrayOf(type, serializedValue)
                    return@launch
                }

                val statement: PreparedStatement = database.connection.prepareStatement(
                    "INSERT OR REPLACE INTO $internalName (key, type, value) VALUES (?,?,?)"
                )

                object : TypeReference<T>() {}

                statement.setString(1, key)
                statement.setString(2, type)
                statement.setString(3, database.mapper.writeValueAsString(value))
                database.commit(statement)

            } catch (e: Exception) {
                if (e.message != null) throw CrimsonDatabaseException(e.message!!)
                else throw e
            }
        }
    }

    /**
     * Retrieves a value by its key from the table.
     *
     * The value is deserialized before being returned. If the key is found in the cache, it is returned from there.
     *
     * @param key the key to look up
     * @return the value associated with the key, or null if not found
     */
    fun get(key: String) : Any? {
        if (database.getCached && database.getCache[internalName]!!.contains(key)) {
            return database.getCache[internalName]!![key]
        }

        val statement: PreparedStatement = database.connection.prepareStatement(
            "SELECT type, value FROM $internalName WHERE key = ?"
        )
        statement.setString(1, key)

        var dbType: String? = null
        var value: String? = null
        statement.executeQuery().use { rs ->
            if (rs.next()) {
                dbType = rs.getString("type")
                value = rs.getString("value")
            }
        }

        if (dbType === null || value === null) {
            return null
        }

        val result = database.mapper.readValue(value, Class.forName(dbType))
        if (database.getCached) database.getCache[internalName]!![key] = result
        return result
    }

    // I need a new function for saving lists.
}

