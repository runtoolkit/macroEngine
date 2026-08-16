package io.runtoolkit.macroengine.systems.hook;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class HookSystems {
	public void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			boolean sneaking = player.isSneaking();
			boolean sprinting = player.isSprinting();
			boolean elytra = player.isFallFlying();
		}
	}
}
