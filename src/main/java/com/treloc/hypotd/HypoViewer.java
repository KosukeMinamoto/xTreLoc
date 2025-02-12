package com.treloc.hypotd;

import java.nio.file.Path;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JOptionPane;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.annotations.XYTitleAnnotation;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RefineryUtilities;
import org.jfree.ui.RectangleEdge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
// import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.awt.geom.Path2D;
import java.awt.BorderLayout;

/*
 * Easy viwer of located results
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to visualize the hypocenter location.
 * @usage: 
 */

public class HypoViewer extends ApplicationFrame {
	private double[][] stationTable;
	private final String[] allCodes;
	private Path[] filePaths;
	private JFreeChart chart;

	private int height = 800;
	private int width;

	public HypoViewer (AppConfig appConfig) {
		super("HypoViewer");
		stationTable = appConfig.getStationTable();
		allCodes = appConfig.getCodes();
		filePaths = appConfig.getDatPaths();

		XYSeriesCollection dataset = new XYSeriesCollection();

		// Station info
		XYSeries stnSeries = loadStationData();
		System.out.println("Station Series: " + stnSeries.getItemCount());
		dataset.addSeries(stnSeries);

		// Seismicity info
		XYSeries hypSeries = loadEventData();
		System.out.println("Hypocenter Series: " + hypSeries.getItemCount());
		dataset.addSeries(hypSeries);

		this.chart = createChart(dataset);
		addStationAnnotations();
		ChartPanel chartPanel = new ChartPanel(chart);
		width = (int) (height * Math.cos(Math.toRadians(getMeanLat())));
		chartPanel.setPreferredSize(new java.awt.Dimension(width, height));

		// Main panel
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(chartPanel, BorderLayout.CENTER);

		// Button(s) panel
		JPanel buttonPanel = createButtoPanel();
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		setContentPane(mainPanel);
		pack();
		RefineryUtilities.centerFrameOnScreen(this);
		setVisible(true);
	}

	private XYSeries loadStationData () {
		XYSeries stnSeries = new XYSeries("Station");
		for (double[] stn : stationTable) {
			stnSeries.add(stn[1], stn[0]);
		}
		return stnSeries;
	}

	private JPanel createButtoPanel () {
		JPanel buttonPanel = new JPanel();
		JButton saveButton = new JButton("Save");
		saveButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					String figName = JOptionPane.showInputDialog("Save fig as: ");
					ChartUtilities.saveChartAsPNG(new File(figName + ".png"), chart, width, height);
					System.out.println("> Chart saved as " + figName + ".png");
				} catch (IOException e2) {
					System.err.println("> Error saving chart: " + e2.getMessage());
				}
			}
		});
		buttonPanel.add(saveButton);

		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				dispose();
				System.exit(0);
			}
		});
		buttonPanel.add(closeButton);

		return buttonPanel;
	}

	private void addStationAnnotations () {
		for (int i = 0; i < stationTable.length; i++) {
			double x = stationTable[i][1];
			double y = stationTable[i][0] * 1.001;
			String stationName = allCodes[i];
			XYTextAnnotation annotation = new XYTextAnnotation(stationName, x, y);
			annotation.setFont(new Font("SansSerif", Font.PLAIN, 10));
			annotation.setPaint(Color.BLACK);
			((XYPlot) chart.getPlot()).addAnnotation(annotation);
		}
	}

	private XYSeries loadEventData () {
		XYSeries hypSeries = new XYSeries("Hypocenter");
		PointsHandler pointsHandler = new PointsHandler();
		for (Path filePath : filePaths) {
			pointsHandler.readDatFile(filePath.toString(), allCodes, 0);
			Point point = pointsHandler.getMainPoint();
			double lat = point.getLat();
			double lon = point.getLon();
			hypSeries.add(lon, lat);
		}
		return hypSeries;
	}

	private double getMeanLat() {
		double mean = 0;
		for (double[] stn : stationTable) {
			mean += stn[0];
		}
		return mean / stationTable.length;
	}

	private JFreeChart createChart(XYDataset dataset) {
		JFreeChart chart = ChartFactory.createScatterPlot(
				"Seismicity",
				"Longitude",
				"Latitude",
				dataset,
				PlotOrientation.VERTICAL,
				false,
				true,
				false);

		XYPlot plot = (XYPlot) chart.getPlot();
		plot.setBackgroundPaint(new Color(255, 255, 255, 0));

		// Legend
		LegendTitle lt = new LegendTitle(plot);
		lt.setItemFont(new Font("SanSerif", Font.BOLD, 12)); // Monospaced
		lt.setBackgroundPaint(Color.WHITE);
		lt.setFrame(new BlockBorder(Color.BLACK));
		lt.setPosition(RectangleEdge.BOTTOM);
		XYTitleAnnotation ta = new XYTitleAnnotation(0., 1, lt, RectangleAnchor.TOP_LEFT);
		ta.setMaxWidth(0.5);
		plot.addAnnotation(ta);

		// X-axis
		plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
		plot.setDomainGridlineStroke(new BasicStroke(1.0F));

		// Y-axis
		plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
    	plot.setRangeGridlineStroke(new BasicStroke(1.0F));

		XYItemRenderer renderer = new CustomRenderer();
		plot.setRenderer(renderer);
		return chart;
	}

	class CustomRenderer extends XYShapeRenderer {
		@Override
		public Paint getItemPaint(int row, int col) {
			if (row == 0) {
				return Color.BLUE;
			} else if (row == 1) {
				return Color.RED;
			} else {
				return Color.GREEN;
			}
		}

		@Override
		public Shape getItemShape(int row, int col) {
			if (row == 0) {
				// return new Rectangle2D.Double(-3, -3, 6, 6);
				return TriangleDown(0, 0, 10);
			} else if (row == 1) {
				return Star(0, 0, 2, 3 * 2.63, 5, Math.toRadians(-18));
			} else {
				return new Ellipse2D.Double(-3, -3, 6, 6);
			}
		}
	}

	private Shape Star(
		double centerX,
		double centerY,
		double innerRadius, double outerRadius, int numRays,
		double startAngleRad) {
		Path2D path = new Path2D.Double();
		double deltaAngleRad = Math.PI / numRays;
		for (int i = 0; i < numRays * 2; i++) {
			double angleRad = startAngleRad + i * deltaAngleRad;
			double ca = Math.cos(angleRad);
			double sa = Math.sin(angleRad);
			double relX = ca;
			double relY = sa;
			if ((i & 1) == 0) {
				relX *= outerRadius;
				relY *= outerRadius;
			} else {
				relX *= innerRadius;
				relY *= innerRadius;
			}
			if (i == 0) {
				path.moveTo(centerX + relX, centerY + relY);
			} else {
				path.lineTo(centerX + relX, centerY + relY);
			}
		}
		path.closePath();
		return path;
	}

	private Shape TriangleDown(
			double centerX,
			double centerY,
			double size) {
		Path2D path = new Path2D.Double();
		double height = size * Math.sqrt(3) / 2;
		path.moveTo(centerX, centerY + height / 2);
		path.lineTo(centerX - size / 2, centerY - height / 2);
		path.lineTo(centerX + size / 2, centerY - height / 2);
		path.closePath();
		return path;
	}
}
