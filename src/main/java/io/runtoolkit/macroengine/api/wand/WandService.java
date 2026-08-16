package io.runtoolkit.macroengine.api.wand;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WandService {
	public record WandAction(String id, Consumer<ServerPlayerEntity> handler, int cooldownTicks) {}

	private final Map<String, WandAction> actions = new ConcurrentHashMap<>();
	private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
	private long tick;

	public void register(String id, int cooldownTicks, Consumer<ServerPlayerEntity> handler) {
		actions.put(id, new WandAction(id, handler, Math.max(0, cooldownTicks)));
	}

	public void unregister(String id) { actions.remove(id); }
	public void tick() { tick++; }

	public boolean tryUse(ServerPlayerEntity player, String actionId) {
		WandAction action = actions.get(actionId);
		if (action == null) return false;
		String key = player.getUuid() + ":" + actionId;
		Long ready = cooldowns.get(key);
		if (ready != null && tick < ready) return false;
		cooldowns.put(key, tick + action.cooldownTicks());
		action.handler().accept(player);
		return true;
	}

	public void onUseItem(ServerPlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (!stack.isOf(Items.CARROT_ON_A_STICK)) return;
		if (actions.isEmpty()) return;
		String id = actions.containsKey("default") ? "default" : actions.keySet().iterator().next();
		tryUse(player, id);
	}

	public Map<String, WandAction> list() { return Map.copyOf(actions); }
}
