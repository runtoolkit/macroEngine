package io.runtoolkit.macroengine.systems.string;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StringUtil {
	private StringUtil() {}

	public static List<String> split(String s, String delim) {
		List<String> out = new ArrayList<>();
		if (s == null) return out;
		if (delim == null || delim.isEmpty()) { out.add(s); return out; }
		int start = 0, idx;
		while ((idx = s.indexOf(delim, start)) >= 0) {
			out.add(s.substring(start, idx));
			start = idx + delim.length();
		}
		out.add(s.substring(start));
		return out;
	}

	public static String lower(String s) { return s == null ? null : s.toLowerCase(Locale.ROOT); }
	public static String upper(String s) { return s == null ? null : s.toUpperCase(Locale.ROOT); }

	public static String formatTicks(long ticks) {
		long totalSec = ticks / 20;
		long h = totalSec / 3600;
		long m = (totalSec % 3600) / 60;
		long s = totalSec % 60;
		if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
		return String.format("%d:%02d", m, s);
	}
}
