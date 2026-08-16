package io.runtoolkit.macroengine.api.perm;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PermissionService {
	private final Map<UUID, Set<String>> perms = new ConcurrentHashMap<>();
	private final Set<UUID> admins = ConcurrentHashMap.newKeySet();

	public void grant(UUID player, String perm) {
		perms.computeIfAbsent(player, u -> ConcurrentHashMap.newKeySet()).add(perm);
	}

	public void revoke(UUID player, String perm) {
		Set<String> set = perms.get(player);
		if (set != null) set.remove(perm);
	}

	public boolean has(UUID player, String perm) {
		if (admins.contains(player)) return true;
		Set<String> set = perms.get(player);
		return set != null && set.contains(perm);
	}

	public boolean has(ServerPlayerEntity player, String perm) {
		return has(player.getUuid(), perm);
	}

	public void setAdmin(UUID player, boolean admin) {
		if (admin) admins.add(player);
		else admins.remove(player);
	}

	public boolean isAdmin(ServerPlayerEntity player) {
		return admins.contains(player.getUuid()) || player.hasPermissionLevel(2);
	}

	public Set<String> list(UUID player) {
		Set<String> set = perms.get(player);
		return set == null ? Set.of() : Collections.unmodifiableSet(set);
	}

	public void clear() { perms.clear(); admins.clear(); }
}
