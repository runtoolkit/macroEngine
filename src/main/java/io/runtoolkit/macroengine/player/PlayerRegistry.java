package io.runtoolkit.macroengine.player;

import io.runtoolkit.macroengine.MacroEngineMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlayerRegistry {
	private final Map<UUID, PlayerData> byUuid = new ConcurrentHashMap<>();
	private final AtomicInteger nextPid = new AtomicInteger(1);

	public static final class PlayerData {
		public final UUID uuid;
		public final int pid;
		public String name;
		public boolean online;
		public int dialogLoadTicks;
		public boolean dialogOpened;
		public boolean dialogClosed;

		PlayerData(UUID uuid, int pid, String name) {
			this.uuid = uuid;
			this.pid = pid;
			this.name = name;
			this.online = true;
		}
	}

	public void onJoin(ServerPlayerEntity player) {
		UUID id = player.getUuid();
		PlayerData data = byUuid.computeIfAbsent(id, u -> {
			int pid = nextPid.getAndIncrement();
			MacroEngineMod.LOGGER.info("player assign_pid {} → pid={}", player.getName().getString(), pid);
			return new PlayerData(u, pid, player.getName().getString());
		});
		data.online = true;
		data.name = player.getName().getString();
	}

	public void onLeave(ServerPlayerEntity player) {
		PlayerData data = byUuid.get(player.getUuid());
		if (data != null) data.online = false;
	}

	public PlayerData get(UUID uuid) { return byUuid.get(uuid); }

	public void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			PlayerData data = byUuid.get(player.getUuid());
			if (data == null) {
				onJoin(player);
				continue;
			}
			if (data.dialogLoadTicks > 0) data.dialogLoadTicks--;
		}
	}
}
