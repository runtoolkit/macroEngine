package io.runtoolkit.macroengine.fiber;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class FiberRuntime {
	@FunctionalInterface
	public interface Step {
		boolean run(Fiber fiber, MinecraftServer server);
	}

	public static final class Fiber {
		public final int id;
		public final String name;
		public int state;
		public boolean alive = true;
		public final Step step;
		public final Map<String, Object> data = new ConcurrentHashMap<>();

		Fiber(int id, String name, Step step) {
			this.id = id;
			this.name = name;
			this.step = step;
		}
	}

	private final Map<Integer, Fiber> fibers = new ConcurrentHashMap<>();
	private final AtomicInteger nextId = new AtomicInteger(1);

	public Fiber spawn(String name, Step step) {
		int id = nextId.getAndIncrement();
		Fiber f = new Fiber(id, name, step);
		fibers.put(id, f);
		return f;
	}

	public void kill(int id) {
		Fiber f = fibers.remove(id);
		if (f != null) f.alive = false;
	}

	public void resume(int id, MinecraftServer server) {
		Fiber f = fibers.get(id);
		if (f == null || !f.alive) return;
		try {
			if (!f.step.run(f, server)) {
				f.alive = false;
				fibers.remove(id);
			}
		} catch (Exception e) {
			MacroEngineMod.LOGGER.error("Fiber {} failed", id, e);
			f.alive = false;
			fibers.remove(id);
		}
	}

	public int size() { return fibers.size(); }
	public void clear() { fibers.clear(); }
}
