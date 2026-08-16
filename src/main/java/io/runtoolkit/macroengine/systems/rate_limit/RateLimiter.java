package io.runtoolkit.macroengine.systems.rate_limit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {
	private final Map<String, Long> last = new ConcurrentHashMap<>();

	public boolean allow(String channel, long nowTick, int minInterval) {
		Long prev = last.get(channel);
		if (prev != null && nowTick - prev < minInterval) return false;
		last.put(channel, nowTick);
		return true;
	}

	public void clear() { last.clear(); }
}
