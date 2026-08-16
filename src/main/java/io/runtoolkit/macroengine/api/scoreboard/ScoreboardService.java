package io.runtoolkit.macroengine.api.scoreboard;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ScoreboardService {
	public int ensureObjective(MinecraftServer server, String name, String display) {
		String disp = display == null ? name : display.replace("\"", "");
		// silent fail if exists is ok for vanilla; use result
		return MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard objectives add " + name + " dummy \"" + disp + "\"");
	}

	public int setScore(MinecraftServer server, String objective, String holder, int value) {
		ensureObjective(server, objective, objective);
		return MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard players set " + holder + " " + objective + " " + value);
	}

	public int addScore(MinecraftServer server, String objective, String holder, int delta) {
		ensureObjective(server, objective, objective);
		return MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard players add " + holder + " " + objective + " " + delta);
	}

	public int setSidebar(MinecraftServer server, String objective) {
		ensureObjective(server, objective, objective);
		return MacroEngineMod.get().getCommands().runAsServer(server,
			"scoreboard objectives setdisplay sidebar " + objective);
	}

	public void setPlayerScore(ServerPlayerEntity player, String objective, int value) {
		setScore(player.getServer(), objective, player.getNameForScoreboard(), value);
	}
}
