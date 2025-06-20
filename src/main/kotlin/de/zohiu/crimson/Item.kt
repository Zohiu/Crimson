package de.zohiu.crimson

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe


open class CustomItem(
    val crimson: Crimson,
    val itemId: String,
    baseMaterial: Material,
    name: Component,
    lore: List<Component>,
    customModelData: Int,
    unbreakable: Boolean = false
) : Listener {
    open fun use(player: Player, item: ItemStack) {}

    open fun registerEvents() {
        crimson.plugin.server.pluginManager.registerEvents(this, crimson.plugin)
    }
    open fun unregisterEvents() {
        HandlerList.unregisterAll(this)
    }

    open fun registerRecipe() {
        Bukkit.addRecipe(ShapedRecipe(NamespacedKey(crimson.plugin, itemId), customItemStack))
    }
    open fun unregisterRecipe() {
        Bukkit.removeRecipe(NamespacedKey(crimson.plugin, itemId))
    }

    val customItemStack: ItemStack
        get() = field.clone()

    init {
        val item = ItemStack(baseMaterial)
        val meta = item.itemMeta
        meta.itemName(name)
        meta.lore(lore)
        meta.setCustomModelData(customModelData)
        meta.isUnbreakable = unbreakable
        item.itemMeta = meta
        customItemStack = item
    }

    fun isItem(itemStack: ItemStack?): Boolean {
        if (itemStack == null) return false
        if (!itemStack.hasItemMeta()) return false
        if (itemStack.itemMeta == null) return false
        if (!itemStack.itemMeta.hasCustomModelData()) return false
        if (itemStack.itemMeta.customModelData != customItemStack.itemMeta.customModelData) return false
        return true
    }

    @EventHandler
    fun onRightClick(event: PlayerInteractEvent) {
        if (!isItem(event.item)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.action == Action.RIGHT_CLICK_BLOCK && isInteractable(event.clickedBlock!!) && !event.player.isSneaking) return
        use(event.player, event.item!!)
    }

    // No placing any custom items!!
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!isItem(event.itemInHand)) return
        event.isCancelled = true
    }
}

fun isInteractable(block: Block): Boolean {
    val type: Material = block.type
    val interactable = type.isInteractable
    if (!interactable) return false

    return when (type) {
        Material.ACACIA_STAIRS, Material.ANDESITE_STAIRS, Material.BIRCH_STAIRS, Material.BLACKSTONE_STAIRS, Material.BRICK_STAIRS, Material.COBBLESTONE_STAIRS, Material.CRIMSON_STAIRS, Material.DARK_OAK_STAIRS, Material.DARK_PRISMARINE_STAIRS, Material.DIORITE_STAIRS, Material.END_STONE_BRICK_STAIRS, Material.GRANITE_STAIRS, Material.JUNGLE_STAIRS, Material.MOSSY_COBBLESTONE_STAIRS, Material.MOSSY_STONE_BRICK_STAIRS, Material.NETHER_BRICK_STAIRS, Material.OAK_STAIRS, Material.POLISHED_ANDESITE_STAIRS, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, Material.POLISHED_BLACKSTONE_STAIRS, Material.POLISHED_DIORITE_STAIRS, Material.POLISHED_GRANITE_STAIRS, Material.PRISMARINE_BRICK_STAIRS, Material.PRISMARINE_STAIRS, Material.PURPUR_STAIRS, Material.QUARTZ_STAIRS, Material.RED_NETHER_BRICK_STAIRS, Material.RED_SANDSTONE_STAIRS, Material.SANDSTONE_STAIRS, Material.SMOOTH_QUARTZ_STAIRS, Material.SMOOTH_RED_SANDSTONE_STAIRS, Material.SMOOTH_SANDSTONE_STAIRS, Material.SPRUCE_STAIRS, Material.STONE_BRICK_STAIRS, Material.STONE_STAIRS, Material.WARPED_STAIRS, Material.ACACIA_FENCE, Material.BIRCH_FENCE, Material.CRIMSON_FENCE, Material.DARK_OAK_FENCE, Material.JUNGLE_FENCE, Material.MOVING_PISTON, Material.NETHER_BRICK_FENCE, Material.OAK_FENCE, Material.PUMPKIN, Material.REDSTONE_ORE, Material.REDSTONE_WIRE, Material.SPRUCE_FENCE, Material.WARPED_FENCE -> false
        else -> true
    }
}