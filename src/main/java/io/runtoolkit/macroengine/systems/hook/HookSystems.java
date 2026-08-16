package io.runtoolkit.macroengine.systems.hook;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.event.EventBus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HookSystems {
	public record HookState(boolean sneaking, boolean sprinting, boolean elytra, boolean flying) {}

	private final Map<UUID, HookState> prev = new ConcurrentHashMap<>();

	public void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			HookState now = new HookState(
				player.isSneaking(),
				player.isSprinting(),
				player.isFallFlying(),
				player.getAbilities().flying
			);
			HookState before = prev.put(player.getUuid(), now);
			if (before == null) continue;
			// Edge events can be extended; fire ON_JUMP approximation via velocity if needed
		}
	}
}
