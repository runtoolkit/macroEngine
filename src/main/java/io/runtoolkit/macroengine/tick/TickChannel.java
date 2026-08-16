package io.runtoolkit.macroengine.tick;

import net.minecraft.server.MinecraftServer;

public final class TickChannel {
	public final String id;
	public final int rate;
	public final int offset;
	public boolean enabled;
	public final Handler handler;

	@FunctionalInterface
	public interface Handler {
		void run(MinecraftServer server, TickEngine engine);
	}

	public TickChannel(String id, int rate, int offset, boolean enabled, Handler handler) {
		this.id = id;
		this.rate = Math.max(1, rate);
		this.offset = Math.floorMod(offset, this.rate);
		this.enabled = enabled;
		this.handler = handler;
	}

	public boolean isDue(long tickCounter) {
		if (!enabled || rate <= 0) return false;
		return Math.floorMod(tickCounter - offset, rate) == 0;
	}
}
