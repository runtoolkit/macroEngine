package io.runtoolkit.macroengine.queue;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public final class TaskQueue {
	public static final int MAX_DEPTH = 256;

	public record Task(int delayTicks, Consumer<MinecraftServer> action) {}

	private final Deque<Task> tasks = new ArrayDeque<>();

	public void enqueue(int delayTicks, Consumer<MinecraftServer> action) {
		tasks.addLast(new Task(Math.max(0, delayTicks), action));
	}

	public void clear() { tasks.clear(); }
	public int size() { return tasks.size(); }

	public void process(MinecraftServer server) {
		int depth = 0;
		while (!tasks.isEmpty() && depth < MAX_DEPTH) {
			Task head = tasks.peekFirst();
			if (head.delayTicks() > 0) {
				tasks.removeFirst();
				tasks.addFirst(new Task(head.delayTicks() - 1, head.action()));
				return;
			}
			tasks.removeFirst();
			depth++;
			try {
				head.action().accept(server);
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Queued task failed", e);
			}
		}
	}
}
