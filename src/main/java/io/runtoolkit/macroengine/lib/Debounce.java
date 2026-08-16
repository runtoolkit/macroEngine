package io.runtoolkit.macroengine.lib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Debounce<T> {
	private final Map<String, Long> due = new ConcurrentHashMap<>();
	private final Map<String, T> pending = new ConcurrentHashMap<>();

	public void touch(String key, T value, long nowTick, int delayTicks) {
		pending.put(key, value);
		due.put(key, nowTick + delayTicks);
	}

	public void tick(long nowTick, Consumer<T> onFire) {
		due.entrySet().removeIf(e -> {
			if (nowTick < e.getValue()) return false;
			T v = pending.remove(e.getKey());
			if (v != null) onFire.accept(v);
			return true;
		});
	}

	public void clear() { due.clear(); pending.clear(); }
}
