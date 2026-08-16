package io.runtoolkit.macroengine.systems.geo;

import io.runtoolkit.macroengine.MacroEngineMod;
import io.runtoolkit.macroengine.event.EventBus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionWatch {
	public record Region(String id, String dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		public boolean contains(double x, double y, double z) {
			return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
		}
	}

	public record RegionEvent(ServerPlayerEntity player, String regionId, boolean entered) {}

	private final List<Region> regions = new ArrayList<>();
	private final Map<UUID, String> inside = new ConcurrentHashMap<>();

	public void add(Region region) {
		regions.removeIf(r -> r.id.equals(region.id));
		regions.add(region);
	}

	public void remove(String id) {
		regions.removeIf(r -> r.id.equals(id));
	}

	public List<Region> list() { return List.copyOf(regions); }

	public void tick(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			String dim = player.getWorld().getRegistryKey().getValue().toString();
			double x = player.getX(), y = player.getY(), z = player.getZ();
			String found = null;
			for (Region r : regions) {
				if (r.dimension.equals(dim) && r.contains(x, y, z)) {
					found = r.id;
					break;
				}
			}
			UUID id = player.getUuid();
			String prev = inside.get(id);
			if (found == null) {
				if (prev != null) {
					inside.remove(id);
					MacroEngineMod.get().getEvents().fire(EventBus.Type.ON_REGION_LEAVE,
						new RegionEvent(player, prev, false));
				}
			} else if (!found.equals(prev)) {
				if (prev != null) {
					MacroEngineMod.get().getEvents().fire(EventBus.Type.ON_REGION_LEAVE,
						new RegionEvent(player, prev, false));
				}
				inside.put(id, found);
				MacroEngineMod.get().getEvents().fire(EventBus.Type.ON_REGION_ENTER,
					new RegionEvent(player, found, true));
			}
		}
	}
}
