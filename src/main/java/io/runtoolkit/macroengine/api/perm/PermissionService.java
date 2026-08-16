package io.runtoolkit.macroengine.api.perm;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Explicit grants only. Op level does NOT auto-grant ME perms.
 * setAdmin marks ME-admin (all perms). Command binds map perm → required for actions.
 */
public final class PermissionService {
	private final Map<UUID, Set<String>> perms = new ConcurrentHashMap<>();
	private final Set<UUID> admins = ConcurrentHashMap.newKeySet();
	/** named command permission requirements */
	private final Map<String, String> commandRequires = new ConcurrentHashMap<>();

	public boolean grant(UUID player, String perm) {
		return perms.computeIfAbsent(player, u -> ConcurrentHashMap.newKeySet()).add(perm);
	}

	public boolean revoke(UUID player, String perm) {
		Set<String> set = perms.get(player);
		if (set == null) return false;
		return set.remove(perm);
	}

	/** Strict check — only explicit grant or ME-admin. */
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

	public boolean isAdmin(UUID player) {
		return admins.contains(player);
	}

	public boolean isAdmin(ServerPlayerEntity player) {
		return admins.contains(player.getUuid());
	}

	/** Bind a logical command id to a required permission node. */
	public boolean bindCommand(String commandId, String requiredPerm) {
		if (commandRequires.containsKey(commandId)) return false;
		commandRequires.put(commandId, requiredPerm);
		return true;
	}

	public boolean unbindCommand(String commandId) {
		return commandRequires.remove(commandId) != null;
	}

	public boolean canRunCommand(ServerPlayerEntity player, String commandId) {
		String req = commandRequires.get(commandId);
		if (req == null) return true;
		return has(player, req);
	}

	public String requiredPerm(String commandId) {
		return commandRequires.get(commandId);
	}

	public Map<String, String> listCommandBinds() {
		return Map.copyOf(commandRequires);
	}

	public Set<String> list(UUID player) {
		Set<String> set = perms.get(player);
		return set == null ? Set.of() : Collections.unmodifiableSet(set);
	}

	public void clear() {
		perms.clear();
		admins.clear();
		commandRequires.clear();
	}
}
