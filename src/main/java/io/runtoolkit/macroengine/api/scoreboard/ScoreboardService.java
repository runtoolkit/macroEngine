package io.runtoolkit.macroengine.api.scoreboard;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Scoreboard helpers via command execution (stable across Yarn renames).
 */
public final class ScoreboardService {
	public void ensureObjective(MinecraftServer server, String name, String display) {
		MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard objectives add " + name + " dummy " + (display == null ? name : display));
	}

	public void setScore(MinecraftServer server, String objective, String holder, int value) {
		MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard players set " + holder + " " + objective + " " + value);
	}

	public void addScore(MinecraftServer server, String objective, String holder, int delta) {
		MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard players add " + holder + " " + objective + " " + delta);
	}

	public void setSidebar(MinecraftServer server, String objective) {
		MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard objectives setdisplay sidebar " + objective);
	}

	public void setPlayerScore(ServerPlayerEntity player, String objective, int value) {
		setScore(player.getServer(), objective, player.getNameForScoreboard(), value);
	}
}
