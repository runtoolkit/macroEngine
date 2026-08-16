package io.runtoolkit.macroengine.event;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Port of events/register + fire.
 * Handlers can be Java consumers or server-command strings (datapack-style func id).
 */
public final class EventBus {
	public enum Type {
		ON_JOIN, ON_LEAVE, ON_DEATH, ON_RESPAWN, ON_JUMP,
		ON_RC, ON_LC, ON_ENCHANT, ON_OPEN_GUI, ON_LOAD,
		ON_REGION_ENTER, ON_REGION_LEAVE, ON_WAND, ON_DIALOG, ON_CBM_INPUT
	}

	public record Handler(String id, BiConsumer<MinecraftServer, Object> action) {}

	private final Map<Type, List<Handler>> listeners = new ConcurrentHashMap<>();
	private final Map<String, List<Handler>> custom = new ConcurrentHashMap<>();

	public void on(Type type, String id, BiConsumer<MinecraftServer, Object> action) {
		listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(new Handler(id, action));
	}

	public void on(Type type, String id, Consumer<Object> action) {
		on(type, id, (server, payload) -> action.accept(payload));
	}

	/** Register a handler that runs a server command when event fires. */
	public void onCommand(Type type, String id, String command) {
		on(type, id, (server, payload) ->
			MacroEngineMod.get().getCommands().runAsServer(server, command));
	}

	public void onCustom(String event, String id, BiConsumer<MinecraftServer, Object> action) {
		custom.computeIfAbsent(event, e -> new ArrayList<>()).add(new Handler(id, action));
	}

	public void onCustomCommand(String event, String id, String command) {
		onCustom(event, id, (server, payload) ->
			MacroEngineMod.get().getCommands().runAsServer(server, command));
	}

	public void fire(Type type, Object payload) {
		MinecraftServer server = MacroEngineMod.get() != null ? MacroEngineMod.get().getServer() : null;
		List<Handler> list = listeners.get(type);
		if (list == null) return;
		for (Handler h : List.copyOf(list)) {
			try {
				h.action().accept(server, payload);
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Event {} handler {} failed", type, h.id(), e);
			}
		}
	}

	public void fireCustom(String event, Object payload) {
		MinecraftServer server = MacroEngineMod.get() != null ? MacroEngineMod.get().getServer() : null;
		List<Handler> list = custom.get(event);
		if (list == null) return;
		for (Handler h : List.copyOf(list)) {
			try {
				h.action().accept(server, payload);
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Custom event {} handler {} failed", event, h.id(), e);
			}
		}
	}

	public boolean unregister(Type type, String id) {
		List<Handler> list = listeners.get(type);
		if (list == null) return false;
		return list.removeIf(h -> h.id().equals(id));
	}

	public boolean unregisterCustom(String event, String id) {
		List<Handler> list = custom.get(event);
		if (list == null) return false;
		return list.removeIf(h -> h.id().equals(id));
	}

	public int count(Type type) {
		List<Handler> list = listeners.get(type);
		return list == null ? 0 : list.size();
	}

	public void clear() {
		listeners.clear();
		custom.clear();
	}
}
