package de.zohiu.crimson

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable

internal data class GUIButton(val clickType: ClickType? = null, val action: () -> Unit)

abstract class GUI(val player: Player) {
    companion object : Listener {
        internal val openGUIs: MutableList<Array<Any>> = mutableListOf()
        lateinit var crimson: Crimson

        private fun matchGUI(inventory: InventoryView): GUI? {
            for (entry in openGUIs) {
                if (inventory == entry[1] as InventoryView) {
                    return entry[0] as GUI
                }
            }
            return null
        }

        @EventHandler
        fun onClose(event: InventoryCloseEvent) {
            val gui = matchGUI(event.player.openInventory) ?: return
            openGUIs.removeIf { it[0] == gui }
            gui.onCloseRaw(event)
            object : BukkitRunnable() { override fun run() {
                gui.onClose(event)
            }}.runTaskLater(crimson.plugin, 1)
        }

        @EventHandler
        fun onClick(event: InventoryClickEvent) {
            val gui = matchGUI(event.view) ?: return
            gui.onClickRaw(event)
            val buttons = gui.buttons[event.slot]
            if (buttons == null) gui.onClick(event)
            else {
                event.isCancelled = true
                buttons.forEach { button ->
                    if (button.clickType != null && event.click != button.clickType) return@forEach
                    button.action.invoke()
                    gui.show()
                }
            }
        }
    }

    internal val buttons: HashMap<Int, MutableList<GUIButton>> = HashMap()

    fun show() {
        matchGUI(player.openInventory)?.let { openGUIs.removeIf { it[0] == this } }
        buttons.clear()
        player.openInventory(render())
        openGUIs.add(arrayOf(this, player.openInventory))
    }

    fun registerButton(slot: Int, clickType: ClickType? = null, action: () -> Unit) {
        if (buttons[slot] == null) buttons[slot] = mutableListOf()
        buttons[slot]!!.add(GUIButton(clickType, action))
    }

    fun item(material: Material, name: String, lore: List<String> = listOf()): ItemStack {
        val itemStack = ItemStack(material)
        val itemMeta = itemStack.itemMeta
        itemMeta?.setDisplayName(name)
        itemMeta?.lore = lore
        itemMeta?.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        itemStack.itemMeta = itemMeta
        return itemStack
    }

    abstract fun render() : Inventory
    open fun onClose(event: InventoryCloseEvent) {}
    open fun onCloseRaw(event: InventoryCloseEvent) {}

    open fun onClick(event: InventoryClickEvent) { event.isCancelled = true }
    open fun onClickRaw(event: InventoryClickEvent) { }
}