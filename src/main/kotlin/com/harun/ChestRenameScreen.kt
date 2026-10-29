package com.harun

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component

class ChestRenameScreen(
	private val currentName: String,
	private val returnScreen: Screen,
	private val onSave: (String) -> Unit
) : Screen(Component.literal("Rename chest")) {
	private lateinit var nameInput: EditBox

	override fun init() {
		val panelTop = height / 2 - 56
		nameInput = EditBox(font, width / 2 - 88, panelTop + 36, 176, 20, Component.literal("Chest name"))
		nameInput.setMaxLength(64)
		nameInput.setValue(currentName)
		addRenderableWidget(nameInput)

		addRenderableWidget(
			Button.builder(Component.literal("Save")) { _ -> save() }
				.bounds(width / 2 - 88, panelTop + 64, 84, 20)
				.build()
		)
		addRenderableWidget(
			Button.builder(Component.literal("Cancel")) { _ -> onClose() }
				.bounds(width / 2 + 4, panelTop + 64, 84, 20)
				.build()
		)
		setInitialFocus(nameInput)
	}

	override fun extractRenderState(
		graphics: GuiGraphicsExtractor,
		mouseX: Int,
		mouseY: Int,
		partialTick: Float
	) {
		graphics.fill(0, 0, width, height, 0x60000000)
		val panelLeft = width / 2 - 104
		val panelTop = height / 2 - 56
		graphics.fill(panelLeft, panelTop, panelLeft + 208, panelTop + 112, 0xFF202020.toInt())
		graphics.fill(panelLeft, panelTop, panelLeft + 208, panelTop + 1, 0xFF606060.toInt())
		graphics.fill(panelLeft, panelTop + 111, panelLeft + 208, panelTop + 112, 0xFF606060.toInt())
		graphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + 112, 0xFF606060.toInt())
		graphics.fill(panelLeft + 207, panelTop, panelLeft + 208, panelTop + 112, 0xFF606060.toInt())
		super.extractRenderState(graphics, mouseX, mouseY, partialTick)
		graphics.centeredText(font, title, width / 2, panelTop + 12, 0xFFFFFFFF.toInt())
		graphics.centeredText(font, "Leave empty to remove the name.", width / 2, panelTop + 92, 0xFFA0A0A0.toInt())
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		if (event.key() == 257 || event.key() == 335) {
			save()
			return true
		}
		return super.keyPressed(event)
	}

	override fun onClose() {
		minecraft.gui.setScreen(returnScreen)
	}

	private fun save() {
		onSave(nameInput.value.trim())
		onClose()
	}
}
