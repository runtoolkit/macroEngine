package io.runtoolkit.macroengine.lib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Throttle {
	private final Map<String, Long> last = new ConcurrentHashMap<>();

	public boolean tryAcquire(String key, long nowTick, int cooldownTicks) {
		Long prev = last.get(key);
		if (prev != null && nowTick - prev < cooldownTicks) return false;
		last.put(key, nowTick);
		return true;
	}

	public void clear() { last.clear(); }
}
