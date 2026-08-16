package io.runtoolkit.macroengine.queue;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Port of core/lib/queue_* + process_queue.
 * delay counts down each tick; at 0 the action fires and processing continues
 * up to MAX_DEPTH (datapack used 256).
 */
public final class TaskQueue {
	public static final int MAX_DEPTH = 256;

	public record Task(int delayTicks, String label, Consumer<MinecraftServer> action) {}

	private final Deque<Task> tasks = new ArrayDeque<>();

	public void enqueue(int delayTicks, Consumer<MinecraftServer> action) {
		enqueue(delayTicks, null, action);
	}

	public void enqueue(int delayTicks, String label, Consumer<MinecraftServer> action) {
		tasks.addLast(new Task(Math.max(0, delayTicks), label, action));
	}

	/** Datapack-style: queue a server command string. */
	public void enqueueCommand(int delayTicks, String command) {
		enqueue(delayTicks, command, server ->
			MacroEngineMod.get().getCommands().runAsServer(server, command));
	}

	public void clear() { tasks.clear(); }
	public int size() { return tasks.size(); }

	public void process(MinecraftServer server) {
		int depth = 0;
		while (!tasks.isEmpty() && depth < MAX_DEPTH) {
			Task head = tasks.peekFirst();
			if (head.delayTicks() > 0) {
				tasks.removeFirst();
				tasks.addFirst(new Task(head.delayTicks() - 1, head.label(), head.action()));
				return;
			}
			tasks.removeFirst();
			depth++;
			try {
				head.action().accept(server);
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Queued task failed label={}", head.label(), e);
			}
		}
	}
}
