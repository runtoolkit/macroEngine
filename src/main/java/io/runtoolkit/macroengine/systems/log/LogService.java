package io.runtoolkit.macroengine.systems.log;

import io.runtoolkit.macroengine.MacroEngineMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LogService {
	public record Entry(String level, String message, long tick) {}
	private final List<Entry> buffer = Collections.synchronizedList(new ArrayList<>());

	public void add(String level, String message) {
		long tick = 0;
		try {
			if (MacroEngineMod.get() != null)
				tick = MacroEngineMod.get().getTickEngine().getTickCounter();
		} catch (Exception ignored) {}
		buffer.add(new Entry(level, message, tick));
		while (buffer.size() > 500) buffer.remove(0);
		if ("ERROR".equals(level)) MacroEngineMod.LOGGER.error("[ME] {}", message);
		else if ("WARN".equals(level)) MacroEngineMod.LOGGER.warn("[ME] {}", message);
		else MacroEngineMod.LOGGER.info("[ME] {}", message);
	}

	public void info(String msg) { add("INFO", msg); }
	public void error(String msg) { add("ERROR", msg); }
}
