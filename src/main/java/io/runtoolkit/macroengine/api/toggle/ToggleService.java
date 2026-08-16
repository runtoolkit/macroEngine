package io.runtoolkit.macroengine.api.toggle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Port of api/toggle — named boolean gates. */
public final class ToggleService {
	private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

	public void set(String key, boolean value) { flags.put(key, value); }
	public boolean get(String key) { return flags.getOrDefault(key, false); }
	public boolean toggle(String key) {
		boolean next = !get(key);
		flags.put(key, next);
		return next;
	}
	public Map<String, Boolean> all() { return Map.copyOf(flags); }
	public void clear() { flags.clear(); }
}
