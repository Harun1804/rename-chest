package com.harun

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.phys.BlockHitResult
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object ChestRenameClient : ClientModInitializer {
	private val logger = LoggerFactory.getLogger(ChestRenameClient::class.java)
	private val nameFile: Path = FabricLoader.getInstance().configDir.resolve("chest-rename.properties")
	private val names = mutableMapOf<String, String>()
	private val O = 0xFF2B2B2B.toInt() // outline
	private val Y = 0xFFF5C542.toInt() // badan pensil
	private val S = 0xFFD9A21B.toInt() // bayangan badan
	private val W = 0xFFE8C99B.toInt() // kayu ujung
	private val G = 0xFF404040.toInt() // grafit
	private val P = 0xFFE87A8C.toInt() // penghapus
	private val M = 0xFFBFBFBF.toInt() // pengikat logam

	// 8x8, pensil miring: penghapus di kanan atas, ujung di kiri bawah
	private val PENCIL_ICON = arrayOf(
		".....OO.",
		"....OPPO",
		"...OMPO.",
		"..OYMO..",
		".OYSO...",
		"OWSO....",
		"OGO.....",
		"OO......",
	)

	private fun pencilColor(c: Char): Int? = when (c) {
		'O' -> O
		'Y' -> Y
		'S' -> S
		'W' -> W
		'G' -> G
		'P' -> P
		'M' -> M
		else -> null
	}

	override fun onInitializeClient() {
		loadNames()
		ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
			val chestScreen = screen as? ContainerScreen ?: return@register
			val originalTitle = screen.title
			if (originalTitle != Component.translatable("container.chest") &&
				originalTitle != Component.translatable("container.chestDouble")
			) return@register

			val openedChestPos = targetedChest(client)
			val menuHeight = 114 + chestScreen.menu.rowCount * 18
			val left = (screen.width - 176) / 2
			val top = (screen.height - menuHeight) / 2
			val renameButton = Button.builder(Component.empty()) {
				val pos = openedChestPos ?: targetedChest(client)
				if (pos == null) {
					client.player?.sendOverlayMessage(Component.literal("Look at a chest before renaming it."))
				} else {
					openRenameEditor(client, screen, pos)
				}
			}
				.bounds(left + 5, top + 4, 12, 12)
				.tooltip(Tooltip.create(Component.literal("Rename chest")))
				.build()
			Screens.getWidgets(screen).add(renameButton)

			ScreenEvents.afterExtract(screen).register { _, graphics, mouseX, mouseY, tickProgress ->
				val title = openedChestPos?.let { chestName(client, it) } ?: originalTitle.string
				val label = trimTitle(client, title, 126)
				val titleX = left + 20
				val titleY = top + 6
				val titleRight = maxOf(
					left + 8 + client.font.width(originalTitle),
					titleX + client.font.width(label) + 2
				).coerceAtMost(left + 166)
				graphics.fill(left + 4, top + 5, titleRight, top + 16, 0xFFC6C6C6.toInt())
				graphics.text(client.font, Component.literal(label), titleX, titleY, 0xFF404040.toInt(), false)
				renameButton.extractRenderState(graphics, mouseX, mouseY, tickProgress)

				val iconX = left + 7
				val iconY = top + 6
				for ((row, line) in PENCIL_ICON.withIndex()) {
					for ((col, ch) in line.withIndex()) {
						val color = pencilColor(ch) ?: continue
						graphics.fill(iconX + col, iconY + row, iconX + col + 1, iconY + row + 1, color)
					}
				}
			}
		}

		HudElementRegistry.attachElementAfter(
			VanillaHudElements.CROSSHAIR,
			ChestRename.id("chest_name_overlay"),
			HudElement { graphics, _ ->
				val client = Minecraft.getInstance()
				val pos = targetedChest(client) ?: return@HudElement
				val name = chestName(client, pos) ?: return@HudElement
				val centerX = graphics.guiWidth() / 2
				val textY = graphics.guiHeight() / 2 + 14
				val halfTextWidth = client.font.width(name) / 2

				graphics.fill(
					centerX - halfTextWidth - 4,
					textY - 2,
					centerX + halfTextWidth + 4,
					textY + client.font.lineHeight + 2,
					0x90000000.toInt()
				)
				graphics.centeredText(client.font, name, centerX, textY, 0xFFFFFFFF.toInt())
			}
		)
	}

	private fun openRenameEditor(client: Minecraft, returnScreen: net.minecraft.client.gui.screens.Screen, pos: BlockPos) {
		val keys = chestNameKeys(client, pos)
		val nameKey = keys.first()
		client.gui.setScreen(
			ChestRenameScreen(names[nameKey] ?: chestName(client, pos).orEmpty(), returnScreen) { name ->
				if (name.isBlank()) {
					keys.forEach(names::remove)
				} else {
					keys.drop(1).forEach(names::remove)
					names[nameKey] = name
				}
				saveNames()
			}
		)
	}

	private fun targetedChest(client: Minecraft): BlockPos? {
		val hit = client.hitResult as? BlockHitResult ?: return null
		val level = client.level ?: return null
		val block = level.getBlockState(hit.blockPos).block
		return if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) canonicalChestPos(client, hit.blockPos) else null
	}

	private fun canonicalChestPos(client: Minecraft, pos: BlockPos): BlockPos {
		val level = client.level ?: return pos
		val state = level.getBlockState(pos)
		if (state.block != Blocks.CHEST && state.block != Blocks.TRAPPED_CHEST) return pos
		if (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos

		val partnerPos = ChestBlock.getConnectedBlockPos(pos, state)
		val partnerState = level.getBlockState(partnerPos)
		if (partnerState.block != state.block || partnerState.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos
		return if (pos.compareTo(partnerPos) <= 0) pos else partnerPos
	}

	private fun chestNameKeys(client: Minecraft, pos: BlockPos): List<String> {
		val canonicalPos = canonicalChestPos(client, pos)
		val keys = linkedSetOf(nameKey(client, canonicalPos), nameKey(client, pos))
		val level = client.level
		if (level != null) {
			val state = level.getBlockState(pos)
			if ((state.block == Blocks.CHEST || state.block == Blocks.TRAPPED_CHEST) &&
				state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
			) {
				keys.add(nameKey(client, ChestBlock.getConnectedBlockPos(pos, state)))
			}
		}
		return keys.toList()
	}

	private fun chestName(client: Minecraft, pos: BlockPos): String? =
		chestNameKeys(client, pos).firstNotNullOfOrNull(names::get)

	private fun nameKey(client: Minecraft, pos: BlockPos): String {
		val dimension = client.level?.dimension()?.identifier()?.toString() ?: return ""
		val world = client.getSingleplayerServer()?.serverDirectory?.toString()
			?: client.getCurrentServer()?.ip
			?: "unknown"
		return "$world|$dimension|${pos.x},${pos.y},${pos.z}"
	}

	private fun trimTitle(client: Minecraft, name: String, maxWidth: Int): String {
		if (client.font.width(name) <= maxWidth) return name
		var end = name.length
		while (end > 0 && client.font.width("${name.substring(0, end)}...") > maxWidth) {
			end--
		}
		return "${name.substring(0, end)}..."
	}

	private fun loadNames() {
		if (!Files.exists(nameFile)) return

		try {
			Files.newBufferedReader(nameFile).use { reader ->
				val properties = Properties()
				properties.load(reader)
				properties.forEach { key, value ->
					if (key is String && value is String && value.isNotBlank()) {
						names[key] = value
					}
				}
			}
		} catch (exception: IOException) {
			logger.error("Could not load saved chest names from {}", nameFile, exception)
		}
	}

	private fun saveNames() {
		try {
			Files.createDirectories(nameFile.parent)
			val properties = Properties()
			names.forEach { (key, value) -> properties.setProperty(key, value) }
			Files.newBufferedWriter(nameFile).use { writer ->
				properties.store(writer, "Chest Rename labels")
			}
		} catch (exception: IOException) {
			logger.error("Could not save chest names to {}", nameFile, exception)
		}
	}
}
