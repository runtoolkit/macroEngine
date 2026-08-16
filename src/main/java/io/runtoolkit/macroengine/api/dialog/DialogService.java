package io.runtoolkit.macroengine.api.dialog;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.player.PlayerRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Port of api/dialog open/close/is_open + input/dialog submit.
 * Native Minecraft dialog UI is version-specific; this coordinates state +
 * command-based submit and optional title/actionbar prompts.
 */
public final class DialogService {
	public record Session(UUID player, String id, BiConsumer<ServerPlayerEntity, String> onSubmit, long openedAtTick) {}

	private final Map<UUID, Session> open = new ConcurrentHashMap<>();
	private int defaultCooldownTicks = 20;

	public void setDefaultCooldown(int ticks) {
		this.defaultCooldownTicks = Math.max(0, ticks);
	}

	public void open(ServerPlayerEntity player, BiConsumer<ServerPlayerEntity, String> onSubmit) {
		open(player, "default", onSubmit);
	}

	public void open(ServerPlayerEntity player, String id, BiConsumer<ServerPlayerEntity, String> onSubmit) {
		PlayerRegistry.PlayerData data = MacroEngineMod.get().getPlayers().get(player.getUuid());
		if (data != null) {
			data.dialogLoadTicks = defaultCooldownTicks;
			data.dialogOpened = true;
			data.dialogClosed = false;
		}
		long tick = MacroEngineMod.get().getTickEngine().getTickCounter();
		open.put(player.getUuid(), new Session(player.getUuid(), id, onSubmit, tick));
		player.sendMessage(Text.literal("[ME] Dialog open id=" + id + " — /macroengine dialog submit <text>"), false);
		MacroEngineMod.get().getTitles().title(player, "Dialog", id, 5, 40, 5);
	}

	public void show(ServerPlayerEntity player, String title, String body) {
		MacroEngineMod.get().getTitles().title(player, title == null ? "" : title, body == null ? "" : body, 10, 60, 10);
		player.sendMessage(Text.literal("[ME] " + (body == null ? title : body)), false);
	}

	public boolean isOpen(ServerPlayerEntity player) {
		return open.containsKey(player.getUuid());
	}

	public boolean isOpen(UUID uuid) {
		return open.containsKey(uuid);
	}

	public void close(ServerPlayerEntity player) {
		open.remove(player.getUuid());
		PlayerRegistry.PlayerData data = MacroEngineMod.get().getPlayers().get(player.getUuid());
		if (data != null) {
			data.dialogOpened = false;
			data.dialogClosed = true;
		}
		MacroEngineMod.get().getTitles().clear(player);
	}

	public boolean submit(ServerPlayerEntity player, String value) {
		if (value == null) value = "";
		Session session = open.remove(player.getUuid());
		MacroEngineMod.get().getInput().submitDialog(player, value);
		PlayerRegistry.PlayerData data = MacroEngineMod.get().getPlayers().get(player.getUuid());
		if (data != null) {
			data.dialogOpened = false;
			data.dialogClosed = true;
		}
		if (session != null && session.onSubmit() != null) {
			session.onSubmit().accept(player, value);
			return true;
		}
		return false;
	}

	public void cancel(ServerPlayerEntity player) {
		close(player);
	}

	public Set<UUID> openPlayers() {
		return Set.copyOf(open.keySet());
	}
}
