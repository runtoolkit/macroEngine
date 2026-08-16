package io.runtoolkit.macroengine.tick;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TickEngine {
	private final MacroEngineMod mod;
	private final List<TickChannel> channels = new ArrayList<>();
	private long tickCounter;
	private boolean paused;

	public TickEngine(MacroEngineMod mod) {
		this.mod = mod;
	}

	public void clearChannels() { channels.clear(); }

	public void register(TickChannel channel) {
		if (channels.size() >= mod.getConfig().maxChannels) {
			MacroEngineMod.LOGGER.warn("Channel limit; refused '{}'", channel.id);
			return;
		}
		unregister(channel.id);
		channels.add(channel);
	}

	public boolean unregister(String id) {
		return channels.removeIf(c -> c.id.equals(id));
	}

	public Optional<TickChannel> find(String id) {
		return channels.stream().filter(c -> c.id.equals(id)).findFirst();
	}

	public int channelCount() { return channels.size(); }
	public List<TickChannel> channels() { return List.copyOf(channels); }
	public long getTickCounter() { return tickCounter; }
	public boolean isPaused() { return paused; }
	public void pause() { paused = true; }
	public void resume() { paused = false; }
	public MacroEngineMod mod() { return mod; }

	public void tick(MinecraftServer server) {
		if (paused) return;
		tickCounter++;
		for (TickChannel ch : channels) {
			if (!ch.isDue(tickCounter)) continue;
			try {
				ch.handler.run(server, this);
			} catch (Exception e) {
				MacroEngineMod.LOGGER.error("Channel '{}' failed", ch.id, e);
			}
		}
	}

	public static void runTimeSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getState().epoch++;
	}

	public static void runPlayerSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getPlayers().tick(server);
	}

	public static void runQueueSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getTaskQueue().process(server);
	}

	public static void runHudSystems(MinecraftServer server, TickEngine eng) {}
	public static void runAdminSystems(MinecraftServer server, TickEngine eng) {}

	public static void runInputSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getInput().tick(server);
	}

	public static void runHookSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getHooks().tick(server);
	}

	public static void runGeoSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getRegionWatch().tick(server);
	}

	public static void runFiberSystems(MinecraftServer server, TickEngine eng) {
		eng.mod.getFibers().tickAll(server);
	}

	public String statusText() {
		StringBuilder sb = new StringBuilder();
		sb.append("══ MacroEngine Tick v").append(MacroEngineMod.VERSION).append(" ══\n");
		sb.append(" Counter ").append(tickCounter).append('\n');
		sb.append(" Epoch ").append(mod.getState().epoch).append('\n');
		sb.append(" Paused ").append(paused).append('\n');
		sb.append(" Fibers ").append(mod.getFibers().size()).append('\n');
		sb.append(" Queue ").append(mod.getTaskQueue().size()).append('\n');
		sb.append(" Channels (").append(channels.size()).append("):\n");
		for (TickChannel c : channels) {
			sb.append("  - ").append(c.id)
				.append(" rate=").append(c.rate)
				.append(" off=").append(c.offset)
				.append(" on=").append(c.enabled).append('\n');
		}
		return sb.toString();
	}
}
