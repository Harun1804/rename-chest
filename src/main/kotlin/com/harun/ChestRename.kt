package com.harun

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier

object ChestRename : ModInitializer {
	const val MOD_ID: String = "chest-rename"

	override fun onInitialize() {
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
