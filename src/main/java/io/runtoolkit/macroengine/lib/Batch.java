package io.runtoolkit.macroengine.lib;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Port of core/lib/batch — collect actions, flush in one go. */
public final class Batch {
	private final List<Consumer<MinecraftServer>> pending = new ArrayList<>();

	public void add(Consumer<MinecraftServer> action) {
		pending.add(action);
	}

	public void addCommand(String command) {
		pending.add(server -> MacroEngineMod.get().getCommands().runAsServer(server, command));
	}

	public int flush(MinecraftServer server) {
		int n = 0;
		List<Consumer<MinecraftServer>> copy = new ArrayList<>(pending);
		pending.clear();
		for (Consumer<MinecraftServer> a : copy) {
			try {
				a.accept(server);
				n++;
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Batch action failed", e);
			}
		}
		return n;
	}

	public void clear() { pending.clear(); }
	public int size() { return pending.size(); }
}
