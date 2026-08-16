package io.runtoolkit.macroengine.lib;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public final class Scheduler {
	private record Job(long due, Consumer<MinecraftServer> action) {}
	private final List<Job> jobs = new ArrayList<>();
	private long tick;

	public void after(int delayTicks, Consumer<MinecraftServer> action) {
		jobs.add(new Job(tick + Math.max(0, delayTicks), action));
	}

	public void tick(MinecraftServer server) {
		tick++;
		Iterator<Job> it = jobs.iterator();
		while (it.hasNext()) {
			Job j = it.next();
			if (tick < j.due()) continue;
			it.remove();
			try {
				j.action().accept(server);
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Scheduler job failed", e);
			}
		}
	}

	public void clear() { jobs.clear(); }
}
