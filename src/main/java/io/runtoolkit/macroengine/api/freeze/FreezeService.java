package io.runtoolkit.macroengine.api.freeze;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Port of api/cmd/freeze — lock player position each tick. */
public final class FreezeService {
	private final Map<UUID, Vec3d> frozen = new ConcurrentHashMap<>();

	public void freeze(ServerPlayerEntity player) {
		frozen.put(player.getUuid(), player.getPos());
	}

	public void unfreeze(ServerPlayerEntity player) {
		frozen.remove(player.getUuid());
	}

	public boolean isFrozen(ServerPlayerEntity player) {
		return frozen.containsKey(player.getUuid());
	}

	public void tick() {
		// applied from player tick via MacroEngineMod players channel
	}

	public void apply(ServerPlayerEntity player) {
		Vec3d pos = frozen.get(player.getUuid());
		if (pos == null) return;
		player.requestTeleport(pos.x, pos.y, pos.z);
		player.setVelocity(Vec3d.ZERO);
		player.velocityModified = true;
	}

	public void clear() { frozen.clear(); }
}
