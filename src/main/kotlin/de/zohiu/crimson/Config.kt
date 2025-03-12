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

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

class Config(crimson: Crimson, val name: String) {
    private val file: File = File("${crimson.configPath}${name}.yml")
    private val yamlConfig: FileConfiguration

    init {
        if (!file.exists()) {
            try {
                // TODO: Take default config from resources
                file.createNewFile();
            } catch (exception: IOException) {
                throw Exception("Config file cannot be created", exception)  // Make custom exception here in future
            }
        }

        yamlConfig = YamlConfiguration.loadConfiguration(file)
    }

    /**
     * Completely deletes this config
     */
    fun delete(): Boolean {
        if (!file.exists()) return false
        file.delete()
        return true
    }

    fun get(path: String): Any? {
        // TODO: Config caching
        return yamlConfig.get(path)
    }

    fun set(path: String, value: Any) {
        yamlConfig.set(path, value)
    }

    fun reload() {
        yamlConfig.load(file)
    }

    fun save() {
        yamlConfig.save(file)
    }

    /**
     * The paths array contains arrays with path and default.
     * e.g.: [["path", "default"], ["sword.damage", 10]]
     */
    fun init(paths: Array<Array<Any>>) {
        paths.forEach {
            val name = it[0] as String
            val default = it[1]

            if (!yamlConfig.contains(name)) {
                set(name, default)
            }
        }
        save()
    }
}