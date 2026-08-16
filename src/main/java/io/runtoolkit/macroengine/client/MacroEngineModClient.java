package io.runtoolkit.macroengine.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacroEngineModClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("macroengine-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("MacroEngine client initialized");
	}
}
