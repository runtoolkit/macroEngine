package io.runtoolkit.macroengine.api.cooldown;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Named cooldowns in ticks. */
public final class CooldownService {
	private final Map<String, Long> readyAt = new ConcurrentHashMap<>();
	private long tick;

	public void tick() { tick++; }

	public boolean tryUse(String key, int cooldownTicks) {
		Long ready = readyAt.get(key);
		if (ready != null && tick < ready) return false;
		readyAt.put(key, tick + Math.max(0, cooldownTicks));
		return true;
	}

	public long remaining(String key) {
		Long ready = readyAt.get(key);
		if (ready == null) return 0;
		return Math.max(0, ready - tick);
	}

	public void clear(String key) { readyAt.remove(key); }
	public void clearAll() { readyAt.clear(); }
}
