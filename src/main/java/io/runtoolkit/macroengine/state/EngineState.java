package io.runtoolkit.macroengine.state;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class EngineState {
	public long epoch;
	public final Map<String, Boolean> flags = new ConcurrentHashMap<>();
	public final Map<String, Object> custom = new ConcurrentHashMap<>();

	public void clear() {
		epoch = 0;
		flags.clear();
		custom.clear();
	}
}
