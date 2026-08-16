package io.runtoolkit.macroengine.input;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.regex.Pattern;

public final class InputValidator {
	private static final Pattern TAG_SAFE = Pattern.compile("^[a-z0-9_/.-]+$", Pattern.CASE_INSENSITIVE);
	private static final Pattern INT = Pattern.compile("^-?\\d+$");
	private static final Pattern FLOAT = Pattern.compile("^-?\\d+(\\.\\d+)?$");

	private InputValidator() {}

	public static OptionalInt asInt(String raw) {
		if (raw == null) return OptionalInt.empty();
		String t = raw.trim();
		if (!INT.matcher(t).matches()) return OptionalInt.empty();
		try { return OptionalInt.of(Integer.parseInt(t)); }
		catch (NumberFormatException e) { return OptionalInt.empty(); }
	}

	public static OptionalDouble asFloat(String raw) {
		if (raw == null) return OptionalDouble.empty();
		String t = raw.trim();
		if (!FLOAT.matcher(t).matches()) return OptionalDouble.empty();
		try { return OptionalDouble.of(Double.parseDouble(t)); }
		catch (NumberFormatException e) { return OptionalDouble.empty(); }
	}

	public static Optional<Boolean> asBool(String raw) {
		if (raw == null) return Optional.empty();
		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "true", "1", "yes", "on" -> Optional.of(true);
			case "false", "0", "no", "off" -> Optional.of(false);
			default -> Optional.empty();
		};
	}

	public static boolean isTagSafe(String raw) {
		return raw != null && TAG_SAFE.matcher(raw.trim()).matches();
	}
}
