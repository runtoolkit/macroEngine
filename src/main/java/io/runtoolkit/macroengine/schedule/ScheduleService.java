package io.runtoolkit.macroengine.schedule;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Port of core/lib/schedule*. */
public final class ScheduleService {
	public record Entry(long nextTick, int interval, boolean repeat, String label, Consumer<MinecraftServer> action) {}

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private long tick;

	public void schedule(String key, int delayTicks, Consumer<MinecraftServer> action) {
		entries.put(key, new Entry(tick + Math.max(0, delayTicks), 0, false, key, action));
	}

	public void scheduleCommand(String key, int delayTicks, String command) {
		schedule(key, delayTicks, server ->
			MacroEngineMod.get().getCommands().runAsServer(server, command));
	}

	public void scheduleRepeating(String key, int intervalTicks, Consumer<MinecraftServer> action) {
		int iv = Math.max(1, intervalTicks);
		entries.put(key, new Entry(tick + iv, iv, true, key, action));
	}

	public void scheduleRepeatingCommand(String key, int intervalTicks, String command) {
		scheduleRepeating(key, intervalTicks, server ->
			MacroEngineMod.get().getCommands().runAsServer(server, command));
	}

	public void cancel(String key) { entries.remove(key); }
	public void clear() { entries.clear(); }
	public int size() { return entries.size(); }
	public Map<String, Entry> snapshot() { return Map.copyOf(entries); }

	public void tick(MinecraftServer server) {
		tick++;
		Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Entry> e = it.next();
			Entry entry = e.getValue();
			if (tick < entry.nextTick()) continue;
			try {
				entry.action().accept(server);
			} catch (Exception ex) {
				MacroEngineMod.LOGGER.error("Schedule '{}' failed", e.getKey(), ex);
			}
			if (entry.repeat()) {
				e.setValue(new Entry(tick + entry.interval(), entry.interval(), true, entry.label(), entry.action()));
			} else {
				it.remove();
			}
		}
	}
}
