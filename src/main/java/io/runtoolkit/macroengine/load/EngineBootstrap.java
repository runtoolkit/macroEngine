package io.runtoolkit.macroengine.load;

import io.runtoolkit.macroengine.MacroEngineMod;

public final class EngineBootstrap {
	private EngineBootstrap() {}

	public static void bootstrap(MacroEngineMod mod) {
		mod.getConfig().resetDefaults();
		mod.getState().clear();
		mod.getTaskQueue().clear();
		mod.getSchedules().clear();
		mod.getEvents().clear();
		MacroEngineMod.LOGGER.info("Engine bootstrap OK");
	}
}
