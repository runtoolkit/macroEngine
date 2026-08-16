package io.runtoolkit.macroengine.api.cmd;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Port of api/cmd/* and multi_cmd — executes Minecraft commands without
 * datapack storage macros. Capture-only paths never auto-run; callers
 * must opt in.
 */
public final class CommandService {
	private final List<String> history = new ArrayList<>();
	private static final int MAX_HISTORY = 100;

	public int runAsServer(MinecraftServer server, String command) {
		if (command == null || command.isBlank()) return 0;
		String cmd = stripSlash(command);
		record(cmd);
		ServerCommandSource src = server.getCommandSource().withLevel(4);
		return server.getCommandManager().executeWithPrefix(src, cmd);
	}

	public int runAsPlayer(ServerPlayerEntity player, String command) {
		if (command == null || command.isBlank()) return 0;
		String cmd = stripSlash(command);
		record(cmd);
		return player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), cmd);
	}

	public int runAt(MinecraftServer server, Vec3d pos, String dimensionKey, String command) {
		if (command == null || command.isBlank()) return 0;
		String cmd = stripSlash(command);
		record(cmd);
		ServerCommandSource src = server.getCommandSource()
			.withPosition(pos)
			.withLevel(4);
		return server.getCommandManager().executeWithPrefix(src, cmd);
	}

	public int runSequence(MinecraftServer server, List<String> commands) {
		int total = 0;
		for (String c : commands) total += runAsServer(server, c);
		return total;
	}

	public void give(ServerPlayerEntity player, String itemId, int count) {
		runAsServer(player.getServer(), "give " + player.getName().getString() + " " + itemId + " " + count);
	}

	public void effect(ServerPlayerEntity player, String effectId, int seconds, int amplifier) {
		runAsServer(player.getServer(),
			"effect give " + player.getName().getString() + " " + effectId + " " + seconds + " " + amplifier);
	}

	public void clearEffect(ServerPlayerEntity player) {
		runAsServer(player.getServer(), "effect clear " + player.getName().getString());
	}

	public void gamemode(ServerPlayerEntity player, String mode) {
		runAsServer(player.getServer(), "gamemode " + mode + " " + player.getName().getString());
	}

	public void msg(ServerPlayerEntity player, String message) {
		player.sendMessage(Text.literal(message), false);
	}

	public void actionbar(ServerPlayerEntity player, String message) {
		player.sendMessage(Text.literal(message), true);
	}

	public void kick(ServerPlayerEntity player, String reason) {
		player.networkHandler.disconnect(Text.literal(reason == null ? "Kicked" : reason));
	}

	public void kill(ServerPlayerEntity player) {
		runAsServer(player.getServer(), "kill " + player.getName().getString());
	}

	public void heal(ServerPlayerEntity player) {
		player.setHealth(player.getMaxHealth());
		player.getHungerManager().setFoodLevel(20);
	}

	public void tp(ServerPlayerEntity player, double x, double y, double z) {
		player.requestTeleport(x, y, z);
	}

	public List<String> history() {
		return List.copyOf(history);
	}

	private void record(String cmd) {
		history.add(cmd);
		while (history.size() > MAX_HISTORY) history.remove(0);
		if (MacroEngineMod.get() != null && MacroEngineMod.get().getConfig().debug) {
			MacroEngineMod.LOGGER.info("[cmd] {}", cmd);
		}
	}

	private static String stripSlash(String c) {
		return c.startsWith("/") ? c.substring(1) : c;
	}
}
