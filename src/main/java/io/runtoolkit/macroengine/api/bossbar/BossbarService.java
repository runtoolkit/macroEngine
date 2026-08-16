package io.runtoolkit.macroengine.api.bossbar;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BossbarService {
	private final Map<String, ServerBossBar> bars = new ConcurrentHashMap<>();
	private final Map<String, Integer> maxValues = new ConcurrentHashMap<>();

	public boolean exists(String id) {
		return bars.containsKey(id);
	}

	/** @return false if already exists */
	public boolean add(String id, String title, BossBar.Color color, BossBar.Style style) {
		if (bars.containsKey(id)) return false;
		ServerBossBar bar = new ServerBossBar(Text.literal(title), color, style);
		bar.setVisible(true);
		bars.put(id, bar);
		maxValues.put(id, 100);
		bar.setPercent(1.0f);
		return true;
	}

	public boolean remove(String id) {
		ServerBossBar bar = bars.remove(id);
		maxValues.remove(id);
		if (bar == null) return false;
		bar.clearPlayers();
		bar.setVisible(false);
		return true;
	}

	/** @return false if bar missing */
	public boolean setValue(String id, int value, int max) {
		ServerBossBar bar = bars.get(id);
		if (bar == null) return false;
		int m = Math.max(1, max);
		maxValues.put(id, m);
		float pct = Math.max(0f, Math.min(1f, value / (float) m));
		bar.setPercent(pct);
		bar.setVisible(true);
		return true;
	}

	public boolean setPlayers(String id, ServerPlayerEntity player, boolean add) {
		ServerBossBar bar = bars.get(id);
		if (bar == null) return false;
		if (add) bar.addPlayer(player);
		else bar.removePlayer(player);
		return true;
	}

	public boolean setTitle(String id, String title) {
		ServerBossBar bar = bars.get(id);
		if (bar == null) return false;
		bar.setName(Text.literal(title));
		return true;
	}

	public void clear() {
		bars.values().forEach(b -> {
			b.clearPlayers();
			b.setVisible(false);
		});
		bars.clear();
		maxValues.clear();
	}
}
