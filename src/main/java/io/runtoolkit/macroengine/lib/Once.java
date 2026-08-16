package io.runtoolkit.macroengine.lib;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Once {
	private final Set<String> global = ConcurrentHashMap.newKeySet();
	private final Set<String> perPlayer = ConcurrentHashMap.newKeySet();

	public boolean once(String key) { return global.add(key); }
	public boolean oncePerPlayer(UUID player, String key) { return perPlayer.add(player + ":" + key); }
	public void reset(String key) { global.remove(key); }
	public void clear() { global.clear(); perPlayer.clear(); }
}
