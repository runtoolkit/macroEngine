package io.runtoolkit.macroengine.api.wand;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.event.EventBus;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Full port of api/wand:
 * register / register_cmd / give / give_custom / cooldown / has / list / unregister
 *
 * Items carry custom_data {wand:"tag"}. Right-click (UseItem) dispatches by tag.
 * Default item is carrot_on_a_stick (same as datapack).
 */
public final class WandService {
	public static final String NBT_KEY = "wand";

	public record Bind(String tag, String command, Consumer<ServerPlayerEntity> handler, int cooldownTicks) {}
	public record WandEvent(ServerPlayerEntity player, String tag, ItemStack stack) {}

	private final Map<String, Bind> binds = new ConcurrentHashMap<>();
	/** key = playerUuid + ":" + tag → ready tick */
	private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
	private long tick;
	private boolean enabled = true;

	public void tick() { tick++; }

	public void setEnabled(boolean on) { this.enabled = on; }
	public boolean isEnabled() { return enabled; }

	/** Register command wand (datapack register_cmd). */
	public void register(String tag, int cooldownTicks, String command) {
		binds.put(tag, new Bind(tag, command, null, Math.max(0, cooldownTicks)));
	}

	/** Register Java handler wand. */
	public void register(String tag, int cooldownTicks, Consumer<ServerPlayerEntity> handler) {
		binds.put(tag, new Bind(tag, null, handler, Math.max(0, cooldownTicks)));
	}

	/** Alias used by older commands. */
	public void registerCommand(String id, int cooldownTicks, String command) {
		register(id, cooldownTicks, command);
	}

	public void unregister(String tag) {
		binds.remove(tag);
	}

	public void unregisterAll() {
		binds.clear();
	}

	public boolean has(String tag) {
		return binds.containsKey(tag);
	}

	public Map<String, Bind> list() {
		return Map.copyOf(binds);
	}

	public void setCooldown(ServerPlayerEntity player, String tag, int ticks) {
		cooldowns.put(cdKey(player.getUuid(), tag), tick + Math.max(0, ticks));
	}

	public long cooldownRemaining(ServerPlayerEntity player, String tag) {
		Long ready = cooldowns.get(cdKey(player.getUuid(), tag));
		if (ready == null) return 0;
		return Math.max(0, ready - tick);
	}

	public boolean isOnCooldown(ServerPlayerEntity player, String tag) {
		return cooldownRemaining(player, tag) > 0;
	}

	/** Give carrot_on_a_stick wand (datapack give). */
	public ItemStack give(ServerPlayerEntity player, String tag, String name) {
		return giveCustom(player, Items.CARROT_ON_A_STICK, tag, name, 1);
	}

	/** Give any item as wand (datapack give_custom). Note: UseItem is reliable for carrot_on_a_stick. */
	public ItemStack giveCustom(ServerPlayerEntity player, Item item, String tag, String name, int count) {
		ItemStack stack = new ItemStack(item, Math.max(1, count));
		applyWandTag(stack, tag);
		if (name != null && !name.isBlank()) {
			stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
		}
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		player.giveItemStack(stack);
		return stack;
	}

	public ItemStack giveCustom(ServerPlayerEntity player, String itemId, String tag, String name, int count) {
		Identifier id = Identifier.tryParse(itemId.contains(":") ? itemId : "minecraft:" + itemId);
		Item item = id == null ? Items.CARROT_ON_A_STICK : Registries.ITEM.get(id);
		if (item == null || item == Items.AIR) item = Items.CARROT_ON_A_STICK;
		return giveCustom(player, item, tag, name, count);
	}

	public static void applyWandTag(ItemStack stack, String tag) {
		NbtCompound nbt = new NbtCompound();
		NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (existing != null) {
			nbt = existing.copyNbt();
		}
		nbt.putString(NBT_KEY, tag);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static String readWandTag(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (data == null) return null;
		NbtCompound nbt = data.copyNbt();
		if (!nbt.contains(NBT_KEY)) return null;
		return nbt.getString(NBT_KEY);
	}

	public boolean playerHasWand(ServerPlayerEntity player, String tag) {
		for (ItemStack stack : player.getInventory().main) {
			if (tag.equals(readWandTag(stack))) return true;
		}
		ItemStack off = player.getOffHandStack();
		return tag.equals(readWandTag(off));
	}

	/** UseItem callback entry. */
	public void onUseItem(ServerPlayerEntity player, Hand hand) {
		if (!enabled) return;
		ItemStack stack = player.getStackInHand(hand);
		String tag = readWandTag(stack);
		if (tag == null) {
			// fallback: plain carrot with single default bind (compat)
			if (stack.isOf(Items.CARROT_ON_A_STICK) && binds.size() == 1) {
				tag = binds.keySet().iterator().next();
			} else if (stack.isOf(Items.CARROT_ON_A_STICK) && binds.containsKey("default")) {
				tag = "default";
			} else {
				return;
			}
		}
		tryUse(player, tag, stack);
	}

	public boolean tryUse(ServerPlayerEntity player, String tag) {
		return tryUse(player, tag, player.getMainHandStack());
	}

	public boolean tryUse(ServerPlayerEntity player, String tag, ItemStack stack) {
		Bind bind = binds.get(tag);
		if (bind == null) return false;
		String key = cdKey(player.getUuid(), tag);
		Long ready = cooldowns.get(key);
		if (ready != null && tick < ready) return false;
		cooldowns.put(key, tick + bind.cooldownTicks());

		if (bind.handler() != null) {
			bind.handler().accept(player);
		}
		if (bind.command() != null && !bind.command().isBlank()) {
			MacroEngineMod.get().getCommands().runAsPlayer(player, bind.command());
		}
		MacroEngineMod.get().getEvents().fire(EventBus.Type.ON_WAND, new WandEvent(player, tag, stack));
		return true;
	}

	public List<String> tags() {
		return new ArrayList<>(binds.keySet());
	}

	private static String cdKey(UUID player, String tag) {
		return player + ":" + tag;
	}
}
