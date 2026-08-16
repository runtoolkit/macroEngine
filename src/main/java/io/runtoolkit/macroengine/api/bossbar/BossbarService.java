package io.runtoolkit.macroengine.api.bossbar;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BossbarService {
	private final Map<String, ServerBossBar> bars = new ConcurrentHashMap<>();

	public ServerBossBar add(String id, String title, BossBar.Color color, BossBar.Style style) {
		remove(id);
		ServerBossBar bar = new ServerBossBar(Text.literal(title), color, style);
		bars.put(id, bar);
		return bar;
	}

	public void remove(String id) {
		ServerBossBar bar = bars.remove(id);
		if (bar != null) bar.clearPlayers();
	}

	public void setValue(String id, int value, int max) {
		ServerBossBar bar = bars.get(id);
		if (bar == null) return;
		bar.setMax(Math.max(1, max));
		bar.setValue(Math.max(0, Math.min(value, max)));
	}

	public void setPlayers(String id, ServerPlayerEntity player, boolean add) {
		ServerBossBar bar = bars.get(id);
		if (bar == null) return;
		if (add) bar.addPlayer(player);
		else bar.removePlayer(player);
	}

	public void setTitle(String id, String title) {
		ServerBossBar bar = bars.get(id);
		if (bar != null) bar.setName(Text.literal(title));
	}

	public boolean has(String id) { return bars.containsKey(id); }
	public void clear() {
		bars.values().forEach(ServerBossBar::clearPlayers);
		bars.clear();
	}
}
