package io.runtoolkit.macroengine.systems.math;

public final class VecMath {
	private VecMath() {}

	public static double length(double x, double y, double z) {
		return Math.sqrt(x * x + y * y + z * z);
	}

	public static double[] normalize(double x, double y, double z) {
		double len = length(x, y, z);
		if (len < 1e-9) return new double[]{0, 0, 0};
		return new double[]{x / len, y / len, z / len};
	}

	public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
		return length(x2 - x1, y2 - y1, z2 - z1);
	}
}
