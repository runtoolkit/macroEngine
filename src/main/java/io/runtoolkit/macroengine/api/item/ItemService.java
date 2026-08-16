package io.runtoolkit.macroengine.api.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ItemService {
	public boolean renameHeld(ServerPlayerEntity player, String name) {
		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty()) return false;
		Text text = Text.literal(name);
		stack.set(DataComponentTypes.CUSTOM_NAME, text);
		// 1.21 also has item_name for non-italic default name display
		try {
			stack.set(DataComponentTypes.ITEM_NAME, text);
		} catch (Throwable ignored) {}
		player.playerScreenHandler.syncState();
		return true;
	}

	public boolean clearHeldName(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty()) return false;
		stack.remove(DataComponentTypes.CUSTOM_NAME);
		try {
			stack.remove(DataComponentTypes.ITEM_NAME);
		} catch (Throwable ignored) {}
		player.playerScreenHandler.syncState();
		return true;
	}
}
