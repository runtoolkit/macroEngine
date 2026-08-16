package io.runtoolkit.macroengine.fiber;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
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
		public boolean yielded;
		public final Step step;
		public final Map<String, Object> data = new ConcurrentHashMap<>();

		Fiber(int id, String name, Step step) {
			this.id = id;
			this.name = name;
			this.step = step;
		}
	}

	private final Map<Integer, Fiber> fibers = new ConcurrentHashMap<>();
	private final Map<String, Integer> byName = new ConcurrentHashMap<>();
	private final AtomicInteger nextId = new AtomicInteger(1);

	public Fiber spawn(String name, Step step) {
		Integer old = byName.get(name);
		if (old != null) kill(old);
		int id = nextId.getAndIncrement();
		Fiber f = new Fiber(id, name, step);
		fibers.put(id, f);
		byName.put(name, id);
		return f;
	}

	public Fiber spawnCommand(String name, String command, int times) {
		final int total = Math.max(1, times);
		return spawn(name, (fiber, server) -> {
			int done = (Integer) fiber.data.computeIfAbsent("done", k -> 0);
			if (done >= total) return false;
			MacroEngineMod.get().getCommands().runAsServer(server, command);
			fiber.data.put("done", done + 1);
			return done + 1 < total;
		});
	}

	public boolean isAlive(int id) {
		Fiber f = fibers.get(id);
		return f != null && f.alive;
	}

	public boolean isAlive(String name) {
		Integer id = byName.get(name);
		return id != null && isAlive(id);
	}

	public void kill(int id) {
		Fiber f = fibers.remove(id);
		if (f != null) {
			f.alive = false;
			byName.remove(f.name, id);
		}
	}

	public void kill(String name) {
		Integer id = byName.remove(name);
		if (id != null) {
			Fiber f = fibers.remove(id);
			if (f != null) f.alive = false;
		}
	}

	public void yield(int id) {
		Fiber f = fibers.get(id);
		if (f != null) f.yielded = true;
	}

	public void tickAll(MinecraftServer server) {
		for (Fiber f : new ArrayList<>(fibers.values())) {
			if (!f.alive) continue;
			if (f.yielded) {
				f.yielded = false;
				continue;
			}
			resume(f.id, server);
		}
	}

	public void resume(int id, MinecraftServer server) {
		Fiber f = fibers.get(id);
		if (f == null || !f.alive) return;
		try {
			if (!f.step.run(f, server)) {
				f.alive = false;
				fibers.remove(id);
				byName.remove(f.name, id);
			}
		} catch (Exception e) {
			MacroEngineMod.LOGGER.error("Fiber {} ({}) failed", id, f.name, e);
			f.alive = false;
			fibers.remove(id);
			byName.remove(f.name, id);
		}
	}

	public int size() { return fibers.size(); }
	public void clear() { fibers.clear(); byName.clear(); }
}
