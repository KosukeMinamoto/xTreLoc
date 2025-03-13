package com.treloc.hypotd;

import javax.swing.JFrame;
import java.util.List;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.TextAnchor;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Font;

/**
 * KDistancePlot
 * This class is used to plot the k-distance graph.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class KDistancePlot {
	private static double elbowEps;
	private static double[] kDistances;

	public static void setKDistances(double[] distances, double eps) {
		kDistances = distances;
		elbowEps = eps;
	}

	public static void setKDistances(List<Double> distances, double eps) {
		kDistances = distances.stream().mapToDouble(Double::doubleValue).toArray();
		elbowEps = eps;
	}

	public static void displayChart() {
		XYSeries series = new XYSeries("k-Distance");
		for (int i = 0; i < kDistances.length; i++) {
			series.add(i + 1, kDistances[i]);
		}

		XYSeries elbowSeries = new XYSeries("Elbow");
		for (int i = 0; i < kDistances.length; i++) {
			if (kDistances[i] == elbowEps) {
				elbowSeries.add(i + 1, kDistances[i]);
				break;
			}
		}

		XYSeriesCollection dataset = new XYSeriesCollection(series);
		dataset.addSeries(elbowSeries);

		JFreeChart chart = ChartFactory.createXYLineChart(
				"k-Distance Graph",
				"Points",
				"Distance",
				dataset,
				PlotOrientation.VERTICAL,
				true,
				true,
				false
		);

		XYPlot plot = chart.getXYPlot();
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
		renderer.setSeriesPaint(0, Color.BLUE);
		renderer.setSeriesPaint(1, Color.RED);
		renderer.setSeriesShapesVisible(1, true);
		plot.setRenderer(renderer);

		double[] percentages = {0.7, 0.8, 0.9, 0.95};
		Color[] colors = {Color.GREEN, Color.ORANGE, Color.MAGENTA, Color.CYAN};
		Font labelFont = new Font("SansSerif", Font.PLAIN, 12);
		for (int i = 0; i < percentages.length; i++) {
			int index = (int) (percentages[i] * kDistances.length) - 1;
			if (index >= 0 && index < kDistances.length) {
				ValueMarker marker = new ValueMarker(index + 1);
				marker.setPaint(colors[i]);
				marker.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5.0f}, 0));
				plot.addDomainMarker(marker);

				marker.setLabel(String.format("%.0f%%", percentages[i] * 100));
				marker.setLabelFont(labelFont);
			}
		}

		// Elbow point marker
		int elbowIndex = -1;
		for (int i = 0; i < kDistances.length; i++) {
			if (kDistances[i] == elbowEps) {
				elbowIndex = i;
				break;
			}
		}
		if (elbowIndex != -1) {
			ValueMarker elbowMarker = new ValueMarker(elbowIndex + 1);
			elbowMarker.setPaint(Color.RED);
			elbowMarker.setStroke(new BasicStroke(2.0f));
			elbowMarker.setLabel("Elbow Epsilon");
			elbowMarker.setLabelAnchor(RectangleAnchor.TOP_RIGHT);
			elbowMarker.setLabelTextAnchor(TextAnchor.TOP_LEFT);
			plot.addDomainMarker(elbowMarker);
		}

		chart.getLegend().setPosition(org.jfree.ui.RectangleEdge.TOP);

		ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
		JFrame frame = new JFrame("k-Distance Plot");
		frame.setContentPane(chartPanel);
		frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	public static void main(String[] args) {
		setKDistances(new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0}, elbowEps);
		displayChart();
	}
} 