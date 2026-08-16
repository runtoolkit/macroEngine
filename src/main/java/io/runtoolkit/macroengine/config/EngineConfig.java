package io.runtoolkit.macroengine.config;

public final class EngineConfig {
	public int tickRate = 1;
	public boolean tickPaused = false;
	public int maxChannels = 32;
	public boolean debug = false;
	public int logLevel = 1;
	public boolean sandbox = true;
	public boolean reloadWarn = true;

	public void resetDefaults() {
		tickRate = 1;
		tickPaused = false;
		maxChannels = 32;
		debug = false;
		logLevel = 1;
		sandbox = true;
		reloadWarn = true;
	}
}
