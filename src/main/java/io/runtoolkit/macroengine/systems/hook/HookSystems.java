package io.runtoolkit.macroengine.systems.hook;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.event.EventBus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Port of systems/hook edge detection: sneak, sprint, elytra, fly.
 * Binds fire commands/events on rising edge.
 */
public final class HookSystems {
	public enum Hook {
		SNEAK_START, SNEAK_STOP,
		SPRINT_START, SPRINT_STOP,
		ELYTRA_START, ELYTRA_STOP,
		FLY_START, FLY_STOP
	}

	public record HookState(boolean sneaking, boolean sprinting, boolean elytra, boolean flying) {}
	public record HookEvent(ServerPlayerEntity player, Hook hook) {}

	private final Map<UUID, HookState> prev = new ConcurrentHashMap<>();
	private final Map<Hook, String> commandBinds = new ConcurrentHashMap<>();
	private final Map<Hook, BiConsumer<ServerPlayerEntity, Hook>> handlers = new ConcurrentHashMap<>();
	private boolean enabled = true;

	public void setEnabled(boolean on) { this.enabled = on; }

	public void bindCommand(Hook hook, String command) {
		commandBinds.put(hook, command);
	}

	public void bind(Hook hook, BiConsumer<ServerPlayerEntity, Hook> handler) {
		handlers.put(hook, handler);
	}

	public void unbind(Hook hook) {
		commandBinds.remove(hook);
		handlers.remove(hook);
	}

	public void tick(MinecraftServer server) {
		if (!enabled) return;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			HookState now = new HookState(
				player.isSneaking(),
				player.isSprinting(),
				player.isFallFlying(),
				player.getAbilities().flying
			);
			HookState before = prev.put(player.getUuid(), now);
			if (before == null) continue;

			edge(player, before.sneaking(), now.sneaking(), Hook.SNEAK_START, Hook.SNEAK_STOP);
			edge(player, before.sprinting(), now.sprinting(), Hook.SPRINT_START, Hook.SPRINT_STOP);
			edge(player, before.elytra(), now.elytra(), Hook.ELYTRA_START, Hook.ELYTRA_STOP);
			edge(player, before.flying(), now.flying(), Hook.FLY_START, Hook.FLY_STOP);
		}
	}

	private void edge(ServerPlayerEntity player, boolean was, boolean is, Hook start, Hook stop) {
		if (!was && is) fire(player, start);
		else if (was && !is) fire(player, stop);
	}

	private void fire(ServerPlayerEntity player, Hook hook) {
		MacroEngineMod.get().getEvents().fireCustom("hook:" + hook.name().toLowerCase(),
			new HookEvent(player, hook));
		BiConsumer<ServerPlayerEntity, Hook> h = handlers.get(hook);
		if (h != null) {
			try { h.accept(player, hook); } catch (Exception e) {
				MacroEngineMod.LOGGER.error("Hook handler {}", hook, e);
			}
		}
		String cmd = commandBinds.get(hook);
		if (cmd != null && !cmd.isBlank()) {
			MacroEngineMod.get().getCommands().runAsPlayer(player, cmd);
		}
	}
}
