package io.runtoolkit.macroengine.api.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ItemService {
	public void renameHeld(ServerPlayerEntity player, String name) {
		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty()) return;
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
	}

	public void clearHeldName(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty()) return;
		stack.remove(DataComponentTypes.CUSTOM_NAME);
	}
}
