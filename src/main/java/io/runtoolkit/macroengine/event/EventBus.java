package io.runtoolkit.macroengine.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EventBus {
	public enum Type {
		ON_JOIN, ON_LEAVE, ON_DEATH, ON_RESPAWN, ON_JUMP,
		ON_RC, ON_LC, ON_ENCHANT, ON_OPEN_GUI, ON_LOAD
	}

	private final Map<Type, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

	public void on(Type type, Consumer<Object> listener) {
		listeners.computeIfAbsent(type, t -> new ArrayList<>()).add(listener);
	}

	public void fire(Type type, Object payload) {
		List<Consumer<Object>> list = listeners.get(type);
		if (list == null) return;
		for (Consumer<Object> c : list) {
			try { c.accept(payload); }
			catch (Exception e) {
				io.runtoolkit.macroengine.MacroEngineMod.LOGGER.error("Event {} failed", type, e);
			}
		}
	}

	public void clear() { listeners.clear(); }
}
