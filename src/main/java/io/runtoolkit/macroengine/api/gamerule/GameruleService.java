package io.runtoolkit.macroengine.api.gamerule;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameRules;

public final class GameruleService {
	public void setBool(MinecraftServer server, GameRules.Key<GameRules.BooleanRule> key, boolean value) {
		server.getGameRules().get(key).set(value, server);
	}

	public void setInt(MinecraftServer server, GameRules.Key<GameRules.IntRule> key, int value) {
		server.getGameRules().get(key).set(value, server);
	}

	public boolean getBool(MinecraftServer server, GameRules.Key<GameRules.BooleanRule> key) {
		return server.getGameRules().get(key).get();
	}

	public int getInt(MinecraftServer server, GameRules.Key<GameRules.IntRule> key) {
		return server.getGameRules().get(key).get();
	}
}
