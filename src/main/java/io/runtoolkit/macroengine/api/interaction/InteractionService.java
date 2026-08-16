package io.runtoolkit.macroengine.api.interaction;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Port of api/interaction bind_use / bind_attack.
 * Binds are keyed by target entity UUID; optional command string runs on trigger.
 */
public final class InteractionService {
	public record Bind(String id, UUID target, String command, BiConsumer<ServerPlayerEntity, Entity> handler) {}

	private final Map<String, Bind> useBinds = new ConcurrentHashMap<>();
	private final Map<String, Bind> attackBinds = new ConcurrentHashMap<>();
	private final List<BiConsumer<ServerPlayerEntity, BlockHitResult>> useBlock = new ArrayList<>();
	private boolean enabled = true;

	public void setEnabled(boolean on) { this.enabled = on; }
	public boolean isEnabled() { return enabled; }

	public void bindUse(String id, UUID target, String command) {
		useBinds.put(id, new Bind(id, target, command, null));
	}

	public void bindAttack(String id, UUID target, String command) {
		attackBinds.put(id, new Bind(id, target, command, null));
	}

	public void bindUse(String id, UUID target, BiConsumer<ServerPlayerEntity, Entity> handler) {
		useBinds.put(id, new Bind(id, target, null, handler));
	}

	public void bindAttack(String id, UUID target, BiConsumer<ServerPlayerEntity, Entity> handler) {
		attackBinds.put(id, new Bind(id, target, null, handler));
	}

	public void unbindUse(String id) { useBinds.remove(id); }
	public void unbindAttack(String id) { attackBinds.remove(id); }
	public void remove(String id) { unbindUse(id); unbindAttack(id); }

	public Map<String, Bind> listUse() { return Map.copyOf(useBinds); }
	public Map<String, Bind> listAttack() { return Map.copyOf(attackBinds); }

	public void onUseBlock(BiConsumer<ServerPlayerEntity, BlockHitResult> h) { useBlock.add(h); }

	public void fireUseEntity(ServerPlayerEntity player, Entity entity) {
		if (!enabled) return;
		UUID tid = entity.getUuid();
		for (Bind b : useBinds.values()) {
			if (!tid.equals(b.target())) continue;
			run(b, player, entity);
		}
	}

	public void fireAttackEntity(ServerPlayerEntity player, Entity entity) {
		if (!enabled) return;
		UUID tid = entity.getUuid();
		for (Bind b : attackBinds.values()) {
			if (!tid.equals(b.target())) continue;
			run(b, player, entity);
		}
	}

	public void fireUseBlock(ServerPlayerEntity player, BlockHitResult hit) {
		if (!enabled) return;
		for (var h : useBlock) h.accept(player, hit);
	}

	private void run(Bind b, ServerPlayerEntity player, Entity entity) {
		if (b.handler() != null) {
			b.handler().accept(player, entity);
		}
		if (b.command() != null && !b.command().isBlank()) {
			MacroEngineMod.get().getCommands().runAsPlayer(player, b.command());
		}
	}
}
