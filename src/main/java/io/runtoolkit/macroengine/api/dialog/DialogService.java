package io.runtoolkit.macroengine.api.dialog;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.player.PlayerRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class DialogService {
	private final Map<UUID, BiConsumer<ServerPlayerEntity, String>> pending = new ConcurrentHashMap<>();
	private int defaultCooldownTicks = 20;

	public void open(ServerPlayerEntity player, BiConsumer<ServerPlayerEntity, String> onSubmit) {
		PlayerRegistry.PlayerData data = MacroEngineMod.get().getPlayers().get(player.getUuid());
		if (data != null) {
			data.dialogLoadTicks = defaultCooldownTicks;
			data.dialogOpened = false;
			data.dialogClosed = true;
		}
		pending.put(player.getUuid(), onSubmit);
		player.sendMessage(Text.literal("[ME] Dialog ready — /macroengine dialog submit <text>"), false);
	}

	public boolean submit(ServerPlayerEntity player, String value) {
		if (value == null) value = "";
		MacroEngineMod.get().getInput().submitDialog(player, value);
		BiConsumer<ServerPlayerEntity, String> cb = pending.remove(player.getUuid());
		if (cb != null) {
			cb.accept(player, value);
			return true;
		}
		return false;
	}

	public void cancel(ServerPlayerEntity player) { pending.remove(player.getUuid()); }
}
