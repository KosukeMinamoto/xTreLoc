package com.treloc.xtreloc.app.gui.util;

import java.util.Arrays;

/**
 * Shared color-axis scaling (quantile range) for map color bar and viewer charts.
 */
public final class ColorScaleUtils {

	private ColorScaleUtils() {
	}

	/**
	 * Robust value range for color mapping / chart axes: {@code lowQ}–{@code highQ} quantiles
	 * of finite samples (e.g. 2%–98%) so outliers do not dominate the scale.
	 *
	 * @param values sample values (may contain non-finite entries, which are ignored)
	 * @param lowQ   inclusive low quantile in [0, 1]
	 * @param highQ  inclusive high quantile in [0, 1]
	 * @return {@code [min, max]} with a small expansion when {@code min == max}
	 */
	public static double[] computeAutoColorRange(double[] values, double lowQ, double highQ) {
		if (values == null || values.length == 0) {
			return new double[] { 0.0, 1.0 };
		}
		int count = 0;
		for (double v : values) {
			if (Double.isFinite(v)) {
				count++;
			}
		}
		if (count == 0) {
			return new double[] { 0.0, 1.0 };
		}
		double[] finite = new double[count];
		int j = 0;
		for (double v : values) {
			if (Double.isFinite(v)) {
				finite[j++] = v;
			}
		}
		Arrays.sort(finite);
		int n = finite.length;
		lowQ = Math.max(0.0, Math.min(1.0, lowQ));
		highQ = Math.max(0.0, Math.min(1.0, highQ));
		if (highQ < lowQ) {
			double t = lowQ;
			lowQ = highQ;
			highQ = t;
		}
		int loIdx = (int) Math.floor((n - 1) * lowQ);
		int hiIdx = (int) Math.ceil((n - 1) * highQ);
		loIdx = Math.max(0, Math.min(loIdx, n - 1));
		hiIdx = Math.max(0, Math.min(hiIdx, n - 1));
		if (hiIdx < loIdx) {
			int tmp = loIdx;
			loIdx = hiIdx;
			hiIdx = tmp;
		}
		double min = finite[loIdx];
		double max = finite[hiIdx];
		if (min > max) {
			double t = min;
			min = max;
			max = t;
		}
		if (min == max) {
			double eps = Math.abs(min) > 1e-12 ? Math.ulp(min) * 64 : 1e-6;
			min -= eps;
			max += eps;
		}
		return new double[] { min, max };
	}
}
