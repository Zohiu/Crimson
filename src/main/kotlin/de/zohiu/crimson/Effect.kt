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

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*


/**
 * Sealed class representing different types of actions that can be performed in an effect.
 */
sealed class Action {
    data class Task(val task: () -> Unit) : Action()
    data class Delay(val delay: Long) : Action()
    data class Repeat(val task: () -> Unit, val expression: () -> Boolean, val amount: Int, val delay: Long) : Action()
    data class RepeatAmbiguous(val task: () -> Unit, val expression: () -> Boolean, val delay: Long) : Action()
    data class RepeatForever(val task: () -> Unit, val expression: () -> Boolean, val delay: Long) : Action()
}


/**
 * A class representing an effect that can contain a series of actions, including tasks, delays, and repeating actions.
 *
 * Effects can be started, stopped, or destroyed. The actions within the effect are executed sequentially,
 * with certain actions (like repeating actions) being handled in a loop.
 *
 * @property crimson the Crimson instance that is associated with this effect
 * @property plugin the Bukkit JavaPlugin instance used to schedule tasks
 */
class Effect(val crimson: Crimson, val plugin: JavaPlugin) {
    companion object {
        val running: MutableList<Effect> = mutableListOf()
    }

    /** List of actions in the effect */
    private val actions: MutableList<Action> = mutableListOf()
    /** Queue of actions to be executed */
    private val actionQueue = ArrayDeque<Action>()
    /** List of tasks that are scheduled */
    private val tasks: MutableList<BukkitTask> = mutableListOf()

    /** Flag indicating if the effect should be aborted */
    private var abort = false
    /** Time offset used for scheduling tasks */
    private var offset: Long = 0

    private fun bakeTask(task: () -> Unit, timeOffsetTicks: Long): BukkitTask {
        return object : BukkitRunnable() { override fun run() {
            if (abort) return
            task()
        }}.runTaskLater(plugin, timeOffsetTicks)
    }

    /**
     * Adds a simple task to be executed once.
     *
     * @param task the task to be executed
     * @return the current Effect instance
     */
    fun run(task: () -> Unit): Effect {
        actions.add(Action.Task(task = task))
        return this
    }

    /**
     * Adds a repeating task that will run for a specified amount of times.
     *
     * @param amount the number of times to repeat the task
     * @param delay the delay in ticks between repetitions
     * @param task the task to be executed repeatedly
     * @return the current Effect instance
     */
    fun repeat(amount: Int, delay: Long, task: () -> Unit): Effect {
        actions.add(Action.Repeat(task = task, expression = { true }, amount = amount, delay = delay))
        return this
    }

    /**
     * Adds a task that repeats while the provided expression evaluates to true.
     *
     * @param expression the condition that determines whether the task should continue repeating
     * @param delay the delay in ticks between repetitions
     * @param task the task to be executed repeatedly
     * @return the current Effect instance
     */
    fun repeatWhile(expression: () -> Boolean, delay: Long, task: () -> Unit): Effect {
        actions.add(Action.RepeatAmbiguous(task = task, expression = expression, delay = delay))
        return this
    }

    /**
     * Adds a task that repeats until the provided expression evaluates to true.
     *
     * @param expression the condition that determines whether the task should stop repeating
     * @param delay the delay in ticks between repetitions
     * @param task the task to be executed repeatedly
     * @return the current Effect instance
     */
    fun repeatUntil(expression: () -> Boolean, delay: Long, task: () -> Unit): Effect {
        return repeatWhile({ !expression() }, delay, task)
    }

    /**
     * Adds a task that repeats indefinitely at the specified delay.
     * This will always be the last task in the action list because nothing can come after forever.
     *
     * @param delay the delay in ticks between repetitions
     * @param task the task to be executed repeatedly
     * @return the current Effect instance
     */
    fun repeatForever(delay: Long, task: () -> Unit): Effect {
        actions.add(Action.RepeatForever(task = task, expression = { true }, delay = delay))
        return this
    }

    /**
     * Adds a delay action to wait for the specified number of ticks.
     *
     * @param delay the number of ticks to wait
     * @return the current Effect instance
     */
    fun wait(delay: Long): Effect {
        actions.add(Action.Delay(delay = delay))
        return this
    }

    /**
     * Starts the effect and begins executing all actions in the queue.
     *
     * @param restart whether to restart the effect if it is already running (default: true)
     * @return the current Effect instance
     */
    fun start(restart: Boolean = true): Effect {
        if (!plugin.isEnabled) return this
        if (!running.contains(this)) running.add(this)
        if (restart) stop()

        while (actionQueue.size > 0) {
            if (abort) return this
            when(val action: Action = actionQueue.pollFirst()) { // ?: break
                is Action.Task -> tasks.add(bakeTask(action.task, offset))
                is Action.Delay -> offset += action.delay
                is Action.Repeat -> repeat(action.amount) {
                    tasks.add(bakeTask(action.task, offset))
                    offset += action.delay
                }

                /**
                 * This one can't be baked because it takes an undefined amount of time
                 * That's why it breaks the loop and just calls start() when it finished.
                 * Since the old tasks will already be removed from the queue, this works.
                 */
                is Action.RepeatAmbiguous -> {
                    tasks.add(object : BukkitRunnable() { override fun run() {
                        if (abort) this.cancel()
                        if (!action.expression()) {
                            this.cancel()
                            offset = 0
                            start()
                        }
                        action.task()
                    }}.runTaskTimer(plugin, offset, action.delay.toLong()))
                    return this
                }

                /**
                 * Repeating forever clears the entire action queue because there cannot
                 * be anything after forever. Be careful with this one.
                 */
                is Action.RepeatForever -> {
                    actionQueue.clear()
                    tasks.add(object : BukkitRunnable() { override fun run() {
                        if (abort) this.cancel()
                        action.task()
                    }}.runTaskTimer(plugin, offset, action.delay.toLong()))
                    return this
                }
            }
        }
        return this
    }

    /**
     * Stops all running tasks and clears the action queue.
     */
    fun stop() {
        tasks.forEach {
            it.cancel()
        }
        tasks.clear()
        actionQueue.clear()
        actions.forEach { actionQueue.addLast(it) }
        offset = 0
    }

    /**
     * Destroys the effect, aborting all actions and stopping the effect from running.
     */
    fun destroy() {
        abort = true
        stop()
        running.remove(this)
    }
}
