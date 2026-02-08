package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.*;
import com.treloc.xtreloc.app.gui.service.StyleFactory;
import com.treloc.xtreloc.io.Station;
import org.geotools.styling.StyleBuilder;

import org.geotools.data.DataUtilities;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.map.*;
import org.geotools.swing.JMapFrame;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.style.Style;
import org.locationtech.jts.geom.*;
import org.geotools.geometry.jts.ReferencedEnvelope;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.List;
import org.geotools.swing.MapPane;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

public class MapView {

	private final MapContent map = new MapContent();
	private final JMapFrame frame = new JMapFrame(map);
	private JLabel coordLabel = new JLabel(" ");
	private ColorBarPanel colorBarPanel;
	private int symbolSize = 10;
	private boolean showErrorEllipse = true;
	private ScaleBarPanel scaleBarPanel;
	
	private java.util.List<Hypocenter> lastHypocenters = null;
	private String lastColorColumn = null;
	private double[] lastColorValues = null;
	
	private JButton exportImageButton;
	
	private java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogInfos = new java.util.ArrayList<>();
	private boolean showConnections = false;
	
	private com.treloc.xtreloc.app.gui.model.CatalogInfo colorMapCatalog = null;
	private String colorMapColumn = null;
	private double[] colorMapValues = null;
	
	private JPanel legendPanel;
	
	private JPanel stationSymbolLegendPanel;
	
	public enum ColorPalette {
		BLUE_TO_RED("Blue to Red"),
		VIRIDIS("Viridis"),
		PLASMA("Plasma"),
		COOL_TO_WARM("Cool to Warm"),
		RAINBOW("Rainbow"),
		GRAYSCALE("Grayscale");
		
		private final String displayName;
		
		ColorPalette(String displayName) {
			this.displayName = displayName;
		}
		
		@Override
		public String toString() {
			return displayName;
		}
	}
	
	private ColorPalette currentPalette = ColorPalette.BLUE_TO_RED;

	public MapView() {
		map.setTitle("xTreLoc Map");
		frame.enableToolBar(true);
		frame.enableStatusBar(true);
		frame.setSize(900, 700);
		
		updateGraticule();
		updateMapBackground();
		
		try {
			File logoFile = com.treloc.xtreloc.app.gui.util.AppDirectoryManager.getLogoFile();
			if (logoFile.exists()) {
				ImageIcon logoIcon = new ImageIcon(logoFile.getAbsolutePath());
				Image img = logoIcon.getImage();
				Image scaledImg = img.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
				ImageIcon scaledIcon = new ImageIcon(scaledImg);
				JLabel logoLabel = new JLabel(scaledIcon);
				frame.getToolBar().add(logoLabel, 0);
			}
		} catch (Exception e) {
			System.err.println("Failed to load logo: " + e.getMessage());
		}
		
		frame.getToolBar().add(Box.createHorizontalStrut(10));
		frame.getToolBar().add(coordLabel);
		
		JCheckBox showErrorEllipseCheckBox = new JCheckBox("Error ellipse", showErrorEllipse);
		showErrorEllipseCheckBox.addActionListener(e -> {
			showErrorEllipse = showErrorEllipseCheckBox.isSelected();
			setShowErrorEllipse(showErrorEllipse);
		});
		frame.getToolBar().add(Box.createHorizontalStrut(10));
		frame.getToolBar().add(showErrorEllipseCheckBox);
		
		colorBarPanel = new ColorBarPanel(currentPalette);
		
		scaleBarPanel = new ScaleBarPanel();
		
		setupLegendPanel();
		setupStationSymbolLegendPanel();
		setupMouseListener();
		
		frame.setVisible(false);
		
		SwingUtilities.invokeLater(() -> {
			setupMapOverlays();
		});
		
		SwingUtilities.invokeLater(() -> {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			setupStatusBarExportButton();
		});
	}
	
	private void updateMapBackground() {
		try {
			MapPane mapPane = frame.getMapPane();
			if (mapPane != null) {
				Component mapPaneComponent = (Component) mapPane;
				Color bgColor = UIManager.getColor("Panel.background");
				if (bgColor != null) {
					mapPaneComponent.setBackground(bgColor);
				} else {
					mapPaneComponent.setBackground(Color.WHITE);
				}
			}
			
			Container contentPane = frame.getContentPane();
			if (contentPane != null) {
				Color bgColor = UIManager.getColor("Panel.background");
				if (bgColor != null) {
					contentPane.setBackground(bgColor);
				} else {
					contentPane.setBackground(Color.WHITE);
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to set map background color: " + e.getMessage());
		}
	}
	
	public void updateTheme() {
		updateMapBackground();
		if (frame != null) {
			frame.repaint();
		}
	}
	
	public JMapFrame getFrame() {
		return frame;
	}
	
	/** Repaints the map frame (e.g. after screening filter change). */
	public void repaintMap() {
		if (frame != null) {
			frame.repaint();
		}
	}
	
	private void setupMouseListener() {
		MapPane mapPane = frame.getMapPane();
		Component mapPaneComponent = (Component) mapPane;
		
		mapPaneComponent.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				updateCoordinates(e);
			}
		});
		
		mapPaneComponent.addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				updateCoordinates(e);
			}
		});
		
		mapPaneComponent.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				SwingUtilities.invokeLater(() -> {
					try {
						Thread.sleep(50);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					updateScaleBar();
				});
			}
		});
		
		try {
			javax.swing.Timer timer = new javax.swing.Timer(500, e -> updateScaleBar());
			timer.setRepeats(true);
			timer.start();
		} catch (Exception e) {
		}
	}
	
	private void updateCoordinates(MouseEvent e) {
		try {
			org.geotools.swing.MapPane mapPane = frame.getMapPane();
			ReferencedEnvelope mapBounds = mapPane.getDisplayArea();
			
			int x = e.getX();
			int y = e.getY();
			Component pane = (Component) mapPane;
			int paneWidth = pane.getWidth();
			int paneHeight = pane.getHeight();
			
			if (paneWidth > 0 && paneHeight > 0) {
				double xCoord = mapBounds.getMinX() + (mapBounds.getMaxX() - mapBounds.getMinX()) * x / paneWidth;
				double yCoord = mapBounds.getMaxY() - (mapBounds.getMaxY() - mapBounds.getMinY()) * y / paneHeight;
				
				DecimalFormat df = new DecimalFormat("#.######");
				coordLabel.setText(String.format("Longitude: %s, Latitude: %s", df.format(xCoord), df.format(yCoord)));
			}
		} catch (Exception ex) {
			coordLabel.setText("Coordinate Error");
		}
	}
	
	
	public void setDepthRange(double min, double max) {
		setColorRange(min, max, "Depth (km)");
	}
	
	public void setColorRange(double min, double max, String label) {
		if (colorBarPanel != null) {
			colorBarPanel.setRange(min, max, label);
		}
	}

	/**
	 * Adds a shapefile to the map (supports multiple shapefiles).
	 * 
	 * @param shp the shapefile to add
	 * @return the layer title (used for visibility toggle)
	 * @throws Exception if the shapefile cannot be loaded or displayed
	 */
	public String addShapefile(File shp) throws Exception {
		if (shp == null || !shp.exists()) {
			throw new IllegalArgumentException("Shapefile does not exist: " + (shp != null ? shp.getAbsolutePath() : "null"));
		}
		
		if (!shp.getName().toLowerCase().endsWith(".shp")) {
			throw new IllegalArgumentException("File is not a shapefile (.shp): " + shp.getName());
		}
		
		String baseName = shp.getName().substring(0, shp.getName().length() - 4);
		File parentDir = shp.getParentFile();
		if (parentDir == null) {
			parentDir = new File(System.getProperty("user.dir"));
		}
		
		File shxFile = new File(parentDir, baseName + ".shx");
		File dbfFile = new File(parentDir, baseName + ".dbf");
		
		if (!shxFile.exists()) {
			throw new IllegalArgumentException("Required .shx file not found: " + shxFile.getAbsolutePath());
		}
		if (!dbfFile.exists()) {
			throw new IllegalArgumentException("Required .dbf file not found: " + dbfFile.getAbsolutePath());
		}
		
		org.geotools.api.data.DataStore store = org.geotools.api.data.FileDataStoreFinder.getDataStore(shp);
		if (store == null) {
			throw new Exception("Failed to create DataStore for shapefile: " + shp.getAbsolutePath() + 
				". Make sure the shapefile is valid and all required files (.shp, .shx, .dbf) are present.");
		}
		
		try {
			String[] typeNames = store.getTypeNames();
			if (typeNames == null || typeNames.length == 0) {
				throw new Exception("No type names found in shapefile: " + shp.getAbsolutePath());
			}
			String typeName = typeNames[0];
			
			org.geotools.api.data.FeatureSource featureSource = store.getFeatureSource(typeName);
			if (featureSource == null) {
				throw new Exception("Failed to get FeatureSource from shapefile: " + shp.getAbsolutePath());
			}
			
			String layerTitle = "ShapefileLayer_" + shp.getName() + "_" + System.currentTimeMillis();
			
			Style shapefileStyle = StyleFactory.createShapefileStyle();
			
			try {
				FeatureLayer layer = new FeatureLayer(featureSource, shapefileStyle);
				layer.setTitle(layerTitle);
				map.addLayer(layer);
				forceMapRepaint();
				return layerTitle;
			} catch (Exception layerEx) {
				if (layerEx.getMessage() != null && layerEx.getMessage().contains("Unable to find function")) {
					System.err.println("[MapView] Function evaluation error detected, trying minimal style...");
					try {
						StyleBuilder sb = new StyleBuilder();
						org.geotools.api.style.Stroke minimalStroke = sb.createStroke();
						minimalStroke.setColor(sb.literalExpression(Color.BLUE));
						minimalStroke.setWidth(sb.literalExpression(1.0));
						minimalStroke.setOpacity(sb.literalExpression(1.0));
						org.geotools.api.style.LineSymbolizer minimalLineSymbolizer = sb.createLineSymbolizer(minimalStroke);
						Style minimalStyle = sb.createStyle(minimalLineSymbolizer);
						
						FeatureLayer layer = new FeatureLayer(featureSource, minimalStyle);
						layer.setTitle(layerTitle);
						map.addLayer(layer);
						forceMapRepaint();
						System.err.println("[MapView] Successfully created layer with minimal style");
						return layerTitle;
					} catch (Exception minimalEx) {
						System.err.println("[MapView] Even minimal style failed: " + minimalEx.getMessage());
					}
				}
				java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MapView.class.getName());
				logger.severe("Failed to create FeatureLayer: " + layerEx.getMessage());
				
				System.err.println("========================================");
				System.err.println("FEATURE LAYER CREATION ERROR");
				System.err.println("========================================");
				System.err.println("File: " + shp.getAbsolutePath());
				System.err.println("Error type: " + layerEx.getClass().getName());
				System.err.println("Error message: " + layerEx.getMessage());
				if (layerEx.getCause() != null) {
					System.err.println("Caused by: " + layerEx.getCause().getClass().getName() + 
						": " + layerEx.getCause().getMessage());
				}
				System.err.println("Stack trace:");
				layerEx.printStackTrace(System.err);
				System.err.println("========================================");
				
				java.io.StringWriter sw = new java.io.StringWriter();
				java.io.PrintWriter pw = new java.io.PrintWriter(sw);
				layerEx.printStackTrace(pw);
				logger.severe("Stack trace:\n" + sw.toString());
				
				throw new Exception("Failed to create FeatureLayer: " + layerEx.getMessage(), layerEx);
			}
		} catch (Exception e) {
			try {
				store.dispose();
			} catch (Exception disposeEx) {
			}
			
			java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MapView.class.getName());
			logger.severe("Failed to add shapefile to map: " + e.getMessage());
			
			System.err.println("========================================");
			System.err.println("SHAPEFILE ADD ERROR");
			System.err.println("========================================");
			System.err.println("File: " + shp.getAbsolutePath());
			System.err.println("Error type: " + e.getClass().getName());
			System.err.println("Error message: " + e.getMessage());
			if (e.getCause() != null) {
				System.err.println("Caused by: " + e.getCause().getClass().getName() + 
					": " + e.getCause().getMessage());
			}
			System.err.println("Stack trace:");
			e.printStackTrace(System.err);
			System.err.println("========================================");
			
			java.io.StringWriter sw = new java.io.StringWriter();
			java.io.PrintWriter pw = new java.io.PrintWriter(sw);
			e.printStackTrace(pw);
			logger.severe("Stack trace:\n" + sw.toString());
			
			throw new Exception("Failed to add shapefile to map: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Toggles visibility of a shapefile layer.
	 * @param layerTitle layer title
	 * @param visible true to show, false to hide
	 */
	public void setShapefileLayerVisibility(String layerTitle, boolean visible) {
		for (Layer layer : map.layers()) {
			if (layerTitle.equals(layer.getTitle())) {
				layer.setVisible(visible);
				forceMapRepaint();
				break;
			}
		}
	}
	
	/**
	 * Removes a shapefile layer.
	 * @param layerTitle layer title
	 */
	public void removeShapefileLayer(String layerTitle) {
		java.util.List<Layer> layersToRemove = new java.util.ArrayList<>();
		for (Layer layer : map.layers()) {
			if (layerTitle.equals(layer.getTitle())) {
				layersToRemove.add(layer);
			}
		}
		for (Layer layer : layersToRemove) {
			try {
				layer.dispose();
			} catch (Exception e) {
			}
			map.removeLayer(layer);
		}
		forceMapRepaint();
	}

	public void showHypocenters(List<Hypocenter> hypos) throws Exception {
		showHypocenters(hypos, null, null);
	}
	
	public void addCatalog(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo) {
		catalogInfos.add(catalogInfo);
		updateMultipleCatalogsDisplay();
	}
	
	public void removeCatalog(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo) {
		catalogInfos.remove(catalogInfo);
		updateMultipleCatalogsDisplay();
	}
	
	/**
	 * Clears all catalogs.
	 */
	public void clearAllCatalogs() {
		catalogInfos.clear();
		updateMultipleCatalogsDisplay();
	}
	
	/**
	 * Sets the visibility of a catalog.
	 * 
	 * @param catalogInfo the catalog information
	 * @param visible true to make the catalog visible, false to hide it
	 */
	private java.util.List<Runnable> catalogVisibilityChangeListeners = new java.util.ArrayList<>();
	
	public void addCatalogVisibilityChangeListener(Runnable listener) {
		catalogVisibilityChangeListeners.add(listener);
	}
	
	public void setCatalogVisible(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo, boolean visible) {
		catalogInfo.setVisible(visible);
		updateMultipleCatalogsDisplay();
		for (Runnable listener : catalogVisibilityChangeListeners) {
			listener.run();
		}
	}
	
	/**
	 * Sets whether to show connection lines between corresponding events.
	 * 
	 * @param show true to show connections, false to hide them
	 */
	public void setShowConnections(boolean show) {
		showConnections = show;
		updateMultipleCatalogsDisplay();
	}
	
	/**
	 * Sets whether to show error ellipses for hypocenters that have xerr/yerr.
	 *
	 * @param show true to show error ellipses, false to hide them
	 */
	public void setShowErrorEllipse(boolean show) {
		showErrorEllipse = show;
		if (lastHypocenters != null && !lastHypocenters.isEmpty()) {
			try {
				showHypocenters(lastHypocenters, lastColorColumn, lastColorValues);
			} catch (Exception e) {
				forceMapRepaint();
			}
		} else {
			updateMultipleCatalogsDisplay();
		}
	}
	
	/**
	 * Returns whether error ellipses are shown.
	 */
	public boolean isShowErrorEllipse() {
		return showErrorEllipse;
	}
	
	/**
	 * Updates the display of multiple catalogs.
	 */
	public void updateMultipleCatalogsDisplay() {
		int fromSettings = com.treloc.xtreloc.app.gui.util.AppSettings.load().getSymbolSize();
		this.symbolSize = Math.max(5, Math.min(50, fromSettings));
		
		java.util.List<Layer> layersToRemove = new java.util.ArrayList<>();
		for (Layer layer : map.layers()) {
			if (layer.getTitle() != null && 
				(layer.getTitle().startsWith("HypoLayer") || 
				 layer.getTitle().startsWith("ConnectionLayer") ||
				 layer.getTitle().startsWith("ErrorBarLayer"))) {
				layersToRemove.add(layer);
			}
		}
		for (Layer layer : layersToRemove) {
			try {
				layer.dispose();
			} catch (Exception e) {
			}
			map.removeLayer(layer);
		}
		
		if (catalogInfos.isEmpty()) {
			return;
		}
		
		GeometryFactory gf = new GeometryFactory();
		SimpleFeatureType hypoType;
		SimpleFeatureType connectionType;
		SimpleFeatureType errorBarType;
		
		try {
			hypoType = DataUtilities.createType("Hypo", 
				"geom:Point,time:String,depth:Double");
			connectionType = DataUtilities.createType("Connection", 
				"geom:LineString");
			errorBarType = DataUtilities.createType("ErrorBar", "geom:Polygon");
		} catch (Exception e) {
			java.util.logging.Logger.getLogger(MapView.class.getName())
				.severe("Failed to create feature types: " + e.getMessage());
			return;
		}
		
		DefaultFeatureCollection errorBarCollection = new DefaultFeatureCollection("ErrorBarLayer", errorBarType);
		if (showErrorEllipse) {
			for (com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo : catalogInfos) {
				if (!catalogInfo.isVisible() || catalogInfo.getHypocenters() == null) continue;
				for (Hypocenter h : catalogInfo.getHypocenters()) {
					if (h.xerr > 0 || h.yerr > 0) {
						org.locationtech.jts.geom.Polygon errorEllipse = createErrorEllipse(h.lon, h.lat, h.xerr, h.yerr, gf);
						if (errorEllipse != null) {
							SimpleFeature errorFeature = DataUtilities.template(errorBarType);
							errorFeature.setDefaultGeometry(errorEllipse);
							errorBarCollection.add(errorFeature);
						}
					}
				}
			}
		}
		
		DefaultFeatureCollection connectionCollection = new DefaultFeatureCollection("ConnectionLayer", connectionType);
		
		double minValue = Double.MAX_VALUE;
		double maxValue = Double.MIN_VALUE;
		java.util.Map<Hypocenter, Color> colorMap = new java.util.HashMap<>();
		
		if (colorMapCatalog != null && colorMapValues != null && 
		    colorMapCatalog.getHypocenters() != null && 
		    colorMapCatalog.getHypocenters().size() == colorMapValues.length) {
			for (double val : colorMapValues) {
				if (val < minValue) minValue = val;
				if (val > maxValue) maxValue = val;
			}
			
			java.util.List<Hypocenter> catalogHypocenters = colorMapCatalog.getHypocenters();
			for (int i = 0; i < catalogHypocenters.size() && i < colorMapValues.length; i++) {
				double colorValue = colorMapValues[i];
				double normalized = (maxValue > minValue) 
					? (colorValue - minValue) / (maxValue - minValue) 
					: 0.0;
				Color color = getColorFromPalette((float) normalized);
				colorMap.put(catalogHypocenters.get(i), color);
			}
			
			String label = (colorMapColumn != null) ? colorMapColumn : "Value";
			setColorRange(minValue, maxValue, label);
		}
		
		if (showErrorEllipse && !errorBarCollection.isEmpty()) {
			Style errorBarStyle = StyleFactory.createErrorBarStyle();
			FeatureLayer errorBarLayer = new FeatureLayer(errorBarCollection, errorBarStyle);
			errorBarLayer.setTitle("ErrorBarLayer");
			map.addLayer(errorBarLayer);
		}
		
		for (com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo : catalogInfos) {
			if (!catalogInfo.isVisible() || catalogInfo.getHypocenters() == null || catalogInfo.getHypocenters().isEmpty()) {
				continue;
			}
			
			boolean isColorMapCatalog = (catalogInfo == colorMapCatalog);
			
			if (isColorMapCatalog && !colorMap.isEmpty()) {
				java.util.Map<Color, DefaultFeatureCollection> colorCollections = new java.util.HashMap<>();
				final String catalogName = catalogInfo.getName();
				
				for (Hypocenter h : catalogInfo.getHypocenters()) {
					Color color = colorMap.get(h);
					if (color == null) {
						color = catalogInfo.getColor();
					}
					final Color finalColor = color;
					
					DefaultFeatureCollection col = colorCollections.computeIfAbsent(finalColor, 
						k -> new DefaultFeatureCollection("HypoLayer_" + catalogName + "_" + finalColor.hashCode(), hypoType));
					
					SimpleFeature f = DataUtilities.template(hypoType);
					f.setDefaultGeometry(gf.createPoint(new Coordinate(h.lon, h.lat)));
					f.setAttribute("time", h.time);
					f.setAttribute("depth", h.depth);
					col.add(f);
				}
				
				com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType = catalogInfo.getSymbolType();
				for (java.util.Map.Entry<Color, DefaultFeatureCollection> entry : colorCollections.entrySet()) {
					Color color = entry.getKey();
					DefaultFeatureCollection col = entry.getValue();
					Style style = com.treloc.xtreloc.app.gui.service.StyleFactory.createSymbolStyle(
						symbolType, color, symbolSize);
					FeatureLayer layer = new FeatureLayer(col, style);
					layer.setTitle("HypoLayer_" + catalogInfo.getName() + "_ColorMap");
					map.addLayer(layer);
				}
			} else {
				DefaultFeatureCollection hypoCollection = new DefaultFeatureCollection(
					"HypoLayer_" + catalogInfo.getName(), hypoType);
				
				Color color = catalogInfo.getColor();
				com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType = catalogInfo.getSymbolType();
				
				for (Hypocenter h : catalogInfo.getHypocenters()) {
					SimpleFeature f = DataUtilities.template(hypoType);
					f.setDefaultGeometry(gf.createPoint(new Coordinate(h.lon, h.lat)));
					f.setAttribute("time", h.time);
					f.setAttribute("depth", h.depth);
					hypoCollection.add(f);
				}
				
				Style style = com.treloc.xtreloc.app.gui.service.StyleFactory.createSymbolStyle(
					symbolType, color, symbolSize);
				FeatureLayer layer = new FeatureLayer(hypoCollection, style);
				layer.setTitle("HypoLayer_" + catalogInfo.getName());
				map.addLayer(layer);
			}
		}
		
		if (showConnections && catalogInfos.size() >= 2) {
			java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> visibleCatalogs = new java.util.ArrayList<>();
			for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : catalogInfos) {
				if (info.isVisible() && info.getHypocenters() != null && !info.getHypocenters().isEmpty()) {
					visibleCatalogs.add(info);
				}
			}
			
			if (visibleCatalogs.size() >= 2) {
				java.util.Map<String, java.util.Map<com.treloc.xtreloc.app.gui.model.CatalogInfo, Hypocenter>> timeToCatalogHypocenter = new java.util.HashMap<>();
				
				for (com.treloc.xtreloc.app.gui.model.CatalogInfo catalog : visibleCatalogs) {
					for (Hypocenter h : catalog.getHypocenters()) {
						String normalizedTime = com.treloc.xtreloc.app.gui.model.CatalogInfo.normalizeTime(h.time);
						java.util.Map<com.treloc.xtreloc.app.gui.model.CatalogInfo, Hypocenter> catalogHypocenterMap = 
							timeToCatalogHypocenter.computeIfAbsent(normalizedTime, k -> new java.util.HashMap<>());
						if (!catalogHypocenterMap.containsKey(catalog)) {
							catalogHypocenterMap.put(catalog, h);
						}
					}
				}
				
				for (java.util.Map.Entry<String, java.util.Map<com.treloc.xtreloc.app.gui.model.CatalogInfo, Hypocenter>> entry : timeToCatalogHypocenter.entrySet()) {
					java.util.Map<com.treloc.xtreloc.app.gui.model.CatalogInfo, Hypocenter> catalogHypocenterMap = entry.getValue();
					if (catalogHypocenterMap.size() >= 2) {
						java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> catalogsForTime = new java.util.ArrayList<>(catalogHypocenterMap.keySet());
						
						for (int i = 0; i < catalogsForTime.size() - 1; i++) {
							com.treloc.xtreloc.app.gui.model.CatalogInfo catalog1 = catalogsForTime.get(i);
							com.treloc.xtreloc.app.gui.model.CatalogInfo catalog2 = catalogsForTime.get(i + 1);
							Hypocenter h1 = catalogHypocenterMap.get(catalog1);
							Hypocenter h2 = catalogHypocenterMap.get(catalog2);
							
							Coordinate[] coords = new Coordinate[] {
								new Coordinate(h1.lon, h1.lat),
								new Coordinate(h2.lon, h2.lat)
							};
							org.locationtech.jts.geom.LineString line = gf.createLineString(coords);
							SimpleFeature connectionFeature = DataUtilities.template(connectionType);
							connectionFeature.setDefaultGeometry(line);
							connectionCollection.add(connectionFeature);
						}
					}
				}
			}
			
			if (!connectionCollection.isEmpty()) {
				Color connectionColor = new Color(128, 128, 128, 128);
				Style connectionStyle = com.treloc.xtreloc.app.gui.service.StyleFactory.createConnectionLineStyle(
					connectionColor, 1.0f);
				FeatureLayer connectionLayer = new FeatureLayer(connectionCollection, connectionStyle);
				connectionLayer.setTitle("ConnectionLayer");
				map.addLayer(connectionLayer);
			}
		}
		
		updateLegend();
		forceMapRepaint();
	}
	
	private void forceMapRepaint() {
		try {
			SwingUtilities.invokeLater(() -> {
				try {
					MapPane mapPane = frame.getMapPane();
					if (mapPane != null) {
						Component mapPaneComponent = (Component) mapPane;
					mapPaneComponent.revalidate();
					mapPaneComponent.repaint();
					updateScaleBar();
					updateGraticule();
					}
				} catch (Exception e) {
					java.util.logging.Logger.getLogger(MapView.class.getName())
						.warning("Failed to repaint map: " + e.getMessage());
				}
			});
		} catch (Exception e) {
			java.util.logging.Logger.getLogger(MapView.class.getName())
				.warning("Failed to schedule map update: " + e.getMessage());
		}
	}
	
	private void updateScaleBar() {
		if (scaleBarPanel == null) {
			return;
		}
		try {
			MapPane mapPane = frame.getMapPane();
			if (mapPane == null) {
				return;
			}
			ReferencedEnvelope bounds = mapPane.getDisplayArea();
			if (bounds == null) {
				return;
			}
			Component mapPaneComponent = (Component) mapPane;
			int mapWidth = mapPaneComponent.getWidth();
			if (mapWidth <= 0) {
				return;
			}
			
			double centerLat = (bounds.getMinY() + bounds.getMaxY()) / 2.0;
			double leftLon = bounds.getMinX();
			double rightLon = bounds.getMaxX();
			
			GeodesicData data = Geodesic.WGS84.Inverse(centerLat, leftLon, centerLat, rightLon);
			double mapWidthMeters = data.s12;
			
			double targetPixelWidth = 150.0;
			double metersPerPixel = mapWidthMeters / mapWidth;
			double targetMeters = targetPixelWidth * metersPerPixel;
			
			double[] niceValues = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000, 1000000};
			double niceValue = niceValues[0];
			for (double val : niceValues) {
				if (val <= targetMeters) {
					niceValue = val;
				} else {
					break;
				}
			}
			
			double scaleBarWidthPixels = niceValue / metersPerPixel;
			String unit = "m";
			double displayValue = niceValue;
			if (niceValue >= 1000) {
				displayValue = niceValue / 1000.0;
				unit = "km";
			}
			
			scaleBarPanel.updateScale(scaleBarWidthPixels, displayValue, unit);
			updateScaleBarPosition();
		} catch (Exception e) {
		}
	}
	
	private void updateScaleBarPosition() {
		if (scaleBarPanel == null) {
			return;
		}
		try {
			Container parent = scaleBarPanel.getParent();
			if (parent instanceof JLayeredPane) {
				JLayeredPane layeredPane = (JLayeredPane) parent;
				int margin = 15;
				int x = margin;
				int y = layeredPane.getHeight() - scaleBarPanel.getPreferredSize().height - margin;
				if (y < 0) {
					y = margin;
				}
				if (layeredPane.getWidth() > 0 && layeredPane.getHeight() > 0) {
					scaleBarPanel.setBounds(x, y, scaleBarPanel.getPreferredSize().width, scaleBarPanel.getPreferredSize().height);
				}
			}
		} catch (Exception e) {
		}
	}
	
	private void setupLegendPanel() {
		legendPanel = new JPanel();
		legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
		legendPanel.setBorder(BorderFactory.createTitledBorder("Legend"));
		legendPanel.setOpaque(true);
		legendPanel.setBackground(new Color(255, 255, 255, 220));
		legendPanel.setVisible(false);
	}
	
	private void setupStationSymbolLegendPanel() {
		stationSymbolLegendPanel = new JPanel();
		stationSymbolLegendPanel.setLayout(new BoxLayout(stationSymbolLegendPanel, BoxLayout.Y_AXIS));
		stationSymbolLegendPanel.setBorder(BorderFactory.createTitledBorder("Symbols"));
		stationSymbolLegendPanel.setOpaque(true);
		stationSymbolLegendPanel.setBackground(new Color(255, 255, 255, 220));
		stationSymbolLegendPanel.setVisible(false);
	}
	
	/**
	 * Updates the station symbol legend panel.
	 */
	private void updateStationSymbolLegend() {
		if (stationSymbolLegendPanel == null) {
			return;
		}
		
		stationSymbolLegendPanel.removeAll();
		
		boolean hasStations = false;
		for (Layer layer : map.layers()) {
			if ("StationLayer".equals(layer.getTitle())) {
				hasStations = true;
				break;
			}
		}
		
		if (hasStations) {
			JPanel itemPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
			itemPanel.setOpaque(false);
			
			JLabel symbolLabel = new JLabel() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(Color.BLACK);
					int size = 12;
					int x = (getWidth() - size) / 2;
					int y = (getHeight() - size) / 2;
					int[] xPoints = {x + size/2, x, x + size};
					int[] yPoints = {y + size, y, y};
					g2.fillPolygon(xPoints, yPoints, 3);
					g2.dispose();
				}
			};
			symbolLabel.setPreferredSize(new java.awt.Dimension(20, 20));
			itemPanel.add(symbolLabel);
			
			JLabel nameLabel = new JLabel("Station");
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
			itemPanel.add(nameLabel);
			
			stationSymbolLegendPanel.add(itemPanel);
			stationSymbolLegendPanel.setVisible(true);
		} else {
			stationSymbolLegendPanel.setVisible(false);
		}
		
		stationSymbolLegendPanel.revalidate();
		stationSymbolLegendPanel.repaint();
	}
	
	/**
	 * Updates the legend panel with current catalog information.
	 */
	private void updateLegend() {
		if (legendPanel == null) {
			return;
		}
		
		legendPanel.removeAll();
		
		boolean hasStations = false;
		for (Layer layer : map.layers()) {
			if ("StationLayer".equals(layer.getTitle())) {
				hasStations = true;
				break;
			}
		}
		
		if (hasStations) {
			JPanel itemPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
			itemPanel.setOpaque(false);
			
			JLabel symbolLabel = new JLabel() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(Color.BLACK);
					int size = 12;
					int x = (getWidth() - size) / 2;
					int y = (getHeight() - size) / 2;
					int[] xPoints = {x + size/2, x, x + size};
					int[] yPoints = {y + size, y, y};
					g2.fillPolygon(xPoints, yPoints, 3);
					g2.dispose();
				}
			};
			symbolLabel.setPreferredSize(new java.awt.Dimension(20, 20));
			itemPanel.add(symbolLabel);
			
			JLabel nameLabel = new JLabel("Station");
			nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
			itemPanel.add(nameLabel);
			
			legendPanel.add(itemPanel);
		}
		
		boolean hasVisibleCatalogs = false;
		for (com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo : catalogInfos) {
			if (catalogInfo.isVisible() && catalogInfo.getHypocenters() != null && !catalogInfo.getHypocenters().isEmpty()) {
				hasVisibleCatalogs = true;
				
				JPanel itemPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
				itemPanel.setOpaque(false);
				
				Color color = catalogInfo.getColor();
				com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType = catalogInfo.getSymbolType();
				
				JLabel symbolLabel = new JLabel() {
					@Override
					protected void paintComponent(Graphics g) {
						super.paintComponent(g);
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g2.setColor(color);
						int size = 12;
						int x = (getWidth() - size) / 2;
						int y = (getHeight() - size) / 2;
						
						switch (symbolType) {
							case CIRCLE:
								g2.fillOval(x, y, size, size);
								break;
							case SQUARE:
								g2.fillRect(x, y, size, size);
								break;
							case TRIANGLE:
								int[] xPoints = {x + size/2, x, x + size};
								int[] yPoints = {y, y + size, y + size};
								g2.fillPolygon(xPoints, yPoints, 3);
								break;
							case DIAMOND:
								int[] dxPoints = {x + size/2, x + size, x + size/2, x};
								int[] dyPoints = {y, y + size/2, y + size, y + size/2};
								g2.fillPolygon(dxPoints, dyPoints, 4);
								break;
							case CROSS:
								g2.setStroke(new BasicStroke(2));
								g2.drawLine(x, y + size/2, x + size, y + size/2);
								g2.drawLine(x + size/2, y, x + size/2, y + size);
								break;
							case STAR:
								int centerX = x + size/2;
								int centerY = y + size/2;
								int outerRadius = size/2;
								int innerRadius = size/4;
								int points = 5;
								int[] sxPoints = new int[points * 2];
								int[] syPoints = new int[points * 2];
								for (int i = 0; i < points * 2; i++) {
									double angle = Math.PI * i / points;
									int radius = (i % 2 == 0) ? outerRadius : innerRadius;
									sxPoints[i] = centerX + (int)(radius * Math.cos(angle - Math.PI/2));
									syPoints[i] = centerY + (int)(radius * Math.sin(angle - Math.PI/2));
								}
								g2.fillPolygon(sxPoints, syPoints, points * 2);
								break;
						}
						g2.dispose();
					}
				};
				symbolLabel.setPreferredSize(new java.awt.Dimension(20, 20));
				itemPanel.add(symbolLabel);
				
				JLabel nameLabel = new JLabel(catalogInfo.getName());
				nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
				itemPanel.add(nameLabel);
				
				legendPanel.add(itemPanel);
			}
		}
		
		if (showConnections && catalogInfos.size() >= 2) {
			boolean hasConnections = false;
			for (com.treloc.xtreloc.app.gui.model.CatalogInfo info : catalogInfos) {
				if (info.isVisible() && info.getHypocenters() != null && !info.getHypocenters().isEmpty()) {
					hasConnections = true;
					break;
				}
			}
			
			if (hasConnections) {
				JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
				connectionPanel.setOpaque(false);
				
				JLabel connectionSymbol = new JLabel() {
					@Override
					protected void paintComponent(Graphics g) {
						super.paintComponent(g);
						Graphics2D g2 = (Graphics2D) g.create();
						g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g2.setColor(new Color(128, 128, 128, 128));
						g2.setStroke(new BasicStroke(2));
						g2.drawLine(5, getHeight()/2, getWidth()-5, getHeight()/2);
						g2.dispose();
					}
				};
				connectionSymbol.setPreferredSize(new java.awt.Dimension(20, 20));
				connectionPanel.add(connectionSymbol);
				
				JLabel connectionLabel = new JLabel("Connections");
				connectionLabel.setFont(connectionLabel.getFont().deriveFont(Font.PLAIN, 11f));
				connectionPanel.add(connectionLabel);
				
				legendPanel.add(connectionPanel);
			}
		}
		
		legendPanel.setVisible(hasStations || hasVisibleCatalogs);
		legendPanel.revalidate();
		legendPanel.repaint();
		updateOverlayPositions();
	}
	
	/**
	 * Gets the list of currently registered catalog information.
	 * 
	 * @return a list of catalog information
	 */
	public java.util.List<com.treloc.xtreloc.app.gui.model.CatalogInfo> getCatalogInfos() {
		return new java.util.ArrayList<>(catalogInfos);
	}
	
	public com.treloc.xtreloc.app.gui.model.CatalogInfo findCatalogForHypocenter(Hypocenter hypocenter) {
		for (com.treloc.xtreloc.app.gui.model.CatalogInfo catalog : catalogInfos) {
			if (catalog.getHypocenters() != null && catalog.getHypocenters().contains(hypocenter)) {
				return catalog;
			}
		}
		return null;
	}
	
	/**
	 * Applies color map display to a specific catalog while keeping other catalogs in normal display mode.
	 * 
	 * @param catalogInfo the catalog to apply color map to
	 * @param columnName the name of the column used for coloring
	 * @param values the values for each hypocenter in the catalog
	 */
	public void applyColorMapToCatalog(com.treloc.xtreloc.app.gui.model.CatalogInfo catalogInfo, String columnName, double[] values) {
		if (catalogInfo == null || values == null) {
			clearColorMap();
			return;
		}
		
		colorMapCatalog = catalogInfo;
		colorMapColumn = columnName;
		colorMapValues = values.clone();
		updateMultipleCatalogsDisplay();
	}
	
	/**
	 * Clears the color map display and returns to normal catalog display mode.
	 */
	public void clearColorMap() {
		colorMapCatalog = null;
		colorMapColumn = null;
		colorMapValues = null;
		updateMultipleCatalogsDisplay();
	}
	
	public void showHypocenters(List<Hypocenter> hypos, String colorColumn, double[] colorValues) throws Exception {
		lastHypocenters = hypos;
		lastColorColumn = colorColumn;
		lastColorValues = colorValues;
		
		java.util.List<Layer> layersToRemove = new java.util.ArrayList<>();
		for (Layer layer : map.layers()) {
			if (layer.getTitle() != null && 
				(layer.getTitle().startsWith("HypoLayer") || layer.getTitle().startsWith("ErrorBarLayer"))) {
				layersToRemove.add(layer);
			}
		}
		for (Layer layer : layersToRemove) {
			try {
				layer.dispose();
			} catch (Exception e) {
			}
			map.removeLayer(layer);
		}
		
		if (hypos == null || hypos.isEmpty()) {
			return;
		}
		
		SimpleFeatureType type = DataUtilities.createType("Hypo", 
			"geom:Point,time:String,depth:Double");
		GeometryFactory gf = new GeometryFactory();
		
		double minValue = Double.MAX_VALUE;
		double maxValue = Double.MIN_VALUE;
		java.util.List<Color> colors = new java.util.ArrayList<>();
		
		for (int i = 0; i < hypos.size(); i++) {
			double colorValue = (colorValues != null && i < colorValues.length) 
				? colorValues[i] 
				: hypos.get(i).depth;
			
			if (colorValue < minValue) minValue = colorValue;
			if (colorValue > maxValue) maxValue = colorValue;
		}
		
		for (int i = 0; i < hypos.size(); i++) {
			double colorValue = (colorValues != null && i < colorValues.length) 
				? colorValues[i] 
				: hypos.get(i).depth;
			
			double normalized = (maxValue > minValue) 
				? (colorValue - minValue) / (maxValue - minValue) 
				: 0.0;
			
			Color color = getColorFromPalette((float) normalized);
			colors.add(color);
		}
		
		String label = (colorColumn != null) ? colorColumn : "Depth (km)";
		setColorRange(minValue, maxValue, label);
		
		java.util.Map<Color, DefaultFeatureCollection> colorCollections = new java.util.HashMap<>();
		
		SimpleFeatureType errorBarType = DataUtilities.createType("ErrorBar", "geom:Polygon");
		DefaultFeatureCollection errorBarCollection = new DefaultFeatureCollection("ErrorBarLayer", errorBarType);
		
		for (int i = 0; i < hypos.size(); i++) {
			Hypocenter h = hypos.get(i);
			Color color = colors.get(i);
			
			DefaultFeatureCollection col = colorCollections.computeIfAbsent(color, 
				k -> new DefaultFeatureCollection("HypoLayer_" + color.hashCode(), type));
			
			SimpleFeature f = DataUtilities.template(type);
			f.setDefaultGeometry(gf.createPoint(new Coordinate(h.lon, h.lat)));
			f.setAttribute("time", h.time);
			f.setAttribute("depth", h.depth);
			col.add(f);
			
			if (showErrorEllipse && (h.xerr > 0 || h.yerr > 0)) {
				org.locationtech.jts.geom.Polygon errorEllipse = createErrorEllipse(h.lon, h.lat, h.xerr, h.yerr, gf);
				if (errorEllipse != null) {
					SimpleFeature errorFeature = DataUtilities.template(errorBarType);
					errorFeature.setDefaultGeometry(errorEllipse);
					errorBarCollection.add(errorFeature);
				}
			}
		}
		
		if (showErrorEllipse && !errorBarCollection.isEmpty()) {
			Style errorBarStyle = StyleFactory.createErrorBarStyle();
			FeatureLayer errorBarLayer = new FeatureLayer(errorBarCollection, errorBarStyle);
			errorBarLayer.setTitle("ErrorBarLayer");
			map.addLayer(errorBarLayer);
		}
		
		for (java.util.Map.Entry<Color, DefaultFeatureCollection> entry : colorCollections.entrySet()) {
			Color color = entry.getKey();
			DefaultFeatureCollection col = entry.getValue();
			Style style = StyleFactory.createColorStyle(color.getRed(), color.getGreen(), color.getBlue());
			FeatureLayer layer = new FeatureLayer(col, style);
			layer.setTitle("HypoLayer_" + color.hashCode());
			map.addLayer(layer);
		}
		
		forceMapRepaint();
	}
	
	/**
	 * Creates an error ellipse polygon.
	 * @param lon longitude
	 * @param lat latitude
	 * @param xerr x-direction error (km)
	 * @param yerr y-direction error (km)
	 * @param gf GeometryFactory
	 * @return error ellipse Polygon
	 */
	private org.locationtech.jts.geom.Polygon createErrorEllipse(double lon, double lat, double xerr, double yerr, GeometryFactory gf) {
		final double DEG2KM = 111.32;
		double latRad = Math.toRadians(lat);
		double lonErrDeg = xerr / (DEG2KM * Math.cos(latRad));
		double latErrDeg = yerr / DEG2KM;
		
		int numPoints = 32;
		Coordinate[] coords = new Coordinate[numPoints + 1];
		
		double angle0 = 0.0;
		double x0 = lonErrDeg * Math.cos(angle0);
		double y0 = latErrDeg * Math.sin(angle0);
		coords[0] = new Coordinate(lon + x0, lat + y0);
		
		for (int i = 1; i < numPoints; i++) {
			double angle = 2.0 * Math.PI * i / numPoints;
			double x = lonErrDeg * Math.cos(angle);
			double y = latErrDeg * Math.sin(angle);
			coords[i] = new Coordinate(lon + x, lat + y);
		}
		
		coords[numPoints] = new Coordinate(coords[0]);
		
		LinearRing ring = gf.createLinearRing(coords);
		return gf.createPolygon(ring);
	}
	
	/**
	 * Get color from current palette based on normalized value (0-1).
	 */
	private Color getColorFromPalette(float ratio) {
		switch (currentPalette) {
			case VIRIDIS:
				return viridisColor(ratio);
			case PLASMA:
				return plasmaColor(ratio);
			case COOL_TO_WARM:
				return interpolateColor(new Color(59, 76, 192), new Color(180, 4, 38), ratio);
			case RAINBOW:
				return rainbowColor(ratio);
			case GRAYSCALE:
				int gray = (int) (255 * ratio);
				return new Color(gray, gray, gray);
			case BLUE_TO_RED:
			default:
				return interpolateColor(Color.BLUE, Color.RED, ratio);
		}
	}
	
	private void refreshMapColors() {
		if (lastHypocenters != null && !lastHypocenters.isEmpty()) {
			try {
				showHypocenters(lastHypocenters, lastColorColumn, lastColorValues);
			} catch (Exception e) {
				forceMapRepaint();
			}
		} else {
			forceMapRepaint();
		}
	}
	
	private void setupMapOverlays() {
		SwingUtilities.invokeLater(() -> {
			try {
				Container contentPane = frame.getContentPane();
				if (contentPane == null) {
					return;
				}
				
				if (!(contentPane.getLayout() instanceof BorderLayout)) {
					contentPane.setLayout(new BorderLayout());
				}
				
				BorderLayout layout = (BorderLayout) contentPane.getLayout();
				Component existingNorth = layout.getLayoutComponent(BorderLayout.NORTH);
				Component existingSouth = layout.getLayoutComponent(BorderLayout.SOUTH);
				Component existingWest = layout.getLayoutComponent(BorderLayout.WEST);
				Component existingCenter = layout.getLayoutComponent(BorderLayout.CENTER);
				Component existingEast = layout.getLayoutComponent(BorderLayout.EAST);
				
				if (existingNorth == null) {
					Component toolbar = frame.getToolBar();
					if (toolbar != null && toolbar.getParent() != contentPane) {
						existingNorth = toolbar;
					}
				}
				
				if (existingSouth == null) {
					JPanel statusBar = findStatusBarPanel(contentPane);
					if (statusBar != null) {
						existingSouth = statusBar.getParent() != null ? statusBar.getParent() : statusBar;
					}
				}
				
				if (colorBarPanel.getParent() != null) {
					colorBarPanel.getParent().remove(colorBarPanel);
				}
				if (legendPanel != null && legendPanel.getParent() != null) {
					legendPanel.getParent().remove(legendPanel);
				}
				if (stationSymbolLegendPanel != null && stationSymbolLegendPanel.getParent() != null) {
					stationSymbolLegendPanel.getParent().remove(stationSymbolLegendPanel);
				}
				if (scaleBarPanel != null && scaleBarPanel.getParent() != null) {
					scaleBarPanel.getParent().remove(scaleBarPanel);
				}
				
				Color panelBgColor = UIManager.getColor("Panel.background");
				if (panelBgColor == null) {
					panelBgColor = new Color(240, 240, 240);
				}
				
				colorBarPanel.setOpaque(true);
				colorBarPanel.setBackground(panelBgColor);
				colorBarPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
				java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(contentPane);
				if (parentWindow != null) {
					colorBarPanel.setDialogParent(parentWindow);
				}
				
				colorBarPanel.setRangeChangeListener((min, max) -> {
					String currentLabel = colorBarPanel.getLabel();
					setColorRange(min, max, currentLabel);
					refreshMapColors();
				});
				colorBarPanel.setPaletteChangeCallback(() -> {
					currentPalette = colorBarPanel.getPalette();
					updateMultipleCatalogsDisplay();
					repaintMap();
				});
				
				if (legendPanel != null) {
					legendPanel.setOpaque(true);
					legendPanel.setBackground(panelBgColor);
					legendPanel.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(Color.BLACK, 1),
						BorderFactory.createEmptyBorder(5, 5, 5, 5)));
				}
				
				if (stationSymbolLegendPanel != null) {
					stationSymbolLegendPanel.setOpaque(true);
					stationSymbolLegendPanel.setBackground(panelBgColor);
					stationSymbolLegendPanel.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(Color.BLACK, 1),
						BorderFactory.createEmptyBorder(5, 5, 5, 5)));
				}
				
				JPanel bottomPanel = new JPanel();
				bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
				bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
				bottomPanel.setOpaque(true);
				bottomPanel.setBackground(panelBgColor);
				
				JPanel colorBarLegendPanel = new JPanel();
				colorBarLegendPanel.setLayout(new BoxLayout(colorBarLegendPanel, BoxLayout.X_AXIS));
				colorBarLegendPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
				colorBarLegendPanel.setOpaque(false);
				
				if (exportImageButton != null && exportImageButton.getParent() != null) {
					exportImageButton.getParent().remove(exportImageButton);
				}
				
				colorBarLegendPanel.add(colorBarPanel);
				colorBarLegendPanel.add(Box.createHorizontalStrut(10));
				
				if (legendPanel != null) {
					colorBarLegendPanel.add(legendPanel);
				}
				
				colorBarLegendPanel.add(Box.createHorizontalGlue());
				
				bottomPanel.add(colorBarLegendPanel);
				
				Component mapPaneComponent = (Component) frame.getMapPane();
				Container mapPaneParent = mapPaneComponent != null ? mapPaneComponent.getParent() : null;
				
				if (existingCenter != null) {
					contentPane.remove(existingCenter);
				}
				
				JLayeredPane mapLayeredPane = new JLayeredPane();
				mapLayeredPane.setLayout(null);
				
				JPanel mapContainer = null;
				if (mapPaneComponent != null) {
					if (mapPaneParent != null && mapPaneParent != contentPane) {
						mapPaneParent.remove(mapPaneComponent);
					}
					
					mapLayeredPane.add(mapPaneComponent, JLayeredPane.DEFAULT_LAYER);
					mapPaneComponent.setBounds(0, 0, mapPaneComponent.getWidth() > 0 ? mapPaneComponent.getWidth() : 800, 
						mapPaneComponent.getHeight() > 0 ? mapPaneComponent.getHeight() : 600);
					
					mapContainer = new JPanel(new BorderLayout());
					mapContainer.add(mapLayeredPane, BorderLayout.CENTER);
					contentPane.add(mapContainer, BorderLayout.CENTER);
					
					if (scaleBarPanel != null) {
						scaleBarPanel.setOpaque(false);
						mapLayeredPane.add(scaleBarPanel, JLayeredPane.PALETTE_LAYER);
						updateScaleBar();
					}
					
					mapLayeredPane.addComponentListener(new java.awt.event.ComponentAdapter() {
						@Override
						public void componentResized(java.awt.event.ComponentEvent e) {
							if (mapPaneComponent != null) {
								mapPaneComponent.setBounds(0, 0, mapLayeredPane.getWidth(), mapLayeredPane.getHeight());
							}
							updateScaleBarPosition();
						}
					});
				}
				
				if (existingNorth != null) {
					if (existingNorth.getParent() != contentPane) {
						contentPane.remove(existingNorth);
					}
					contentPane.add(existingNorth, BorderLayout.NORTH);
				} else {
					Component toolbar = frame.getToolBar();
					if (toolbar != null) {
						contentPane.add(toolbar, BorderLayout.NORTH);
					}
				}
				
				if (existingWest != null) {
					if (existingWest.getParent() != contentPane) {
						contentPane.remove(existingWest);
					}
					contentPane.add(existingWest, BorderLayout.WEST);
				}
				
				if (existingEast != null) {
					if (existingEast.getParent() != contentPane) {
						contentPane.remove(existingEast);
					}
					contentPane.add(existingEast, BorderLayout.EAST);
				}
				
				if (existingSouth != null) {
					JPanel southContainer = new JPanel(new BorderLayout());
					southContainer.setOpaque(false);
					southContainer.add(bottomPanel, BorderLayout.NORTH);
					if (existingSouth.getParent() != southContainer) {
						if (existingSouth.getParent() != null) {
							existingSouth.getParent().remove(existingSouth);
						}
						southContainer.add(existingSouth, BorderLayout.SOUTH);
					}
					contentPane.add(southContainer, BorderLayout.SOUTH);
				} else {
					JPanel statusBar = findStatusBarPanel(contentPane);
					if (statusBar != null) {
						JPanel southContainer = new JPanel(new BorderLayout());
						southContainer.setOpaque(false);
						southContainer.add(bottomPanel, BorderLayout.NORTH);
						Container statusBarParent = statusBar.getParent();
						if (statusBarParent != null && statusBarParent != southContainer) {
							statusBarParent.remove(statusBar);
						}
						southContainer.add(statusBar, BorderLayout.SOUTH);
						contentPane.add(southContainer, BorderLayout.SOUTH);
					} else {
						contentPane.add(bottomPanel, BorderLayout.SOUTH);
					}
				}
				
				contentPane.revalidate();
				contentPane.repaint();
			} catch (Exception e) {
				System.err.println("Failed to setup map overlays: " + e.getMessage());
				e.printStackTrace();
			}
		});
	}
	
	private void updateOverlayPositions() {
		updateScaleBarPosition();
	}
	
	private void updateGraticule() {
		java.util.List<Layer> layersToRemove = new java.util.ArrayList<>();
		for (Layer layer : map.layers()) {
			if ("GraticuleLayer".equals(layer.getTitle())) {
				layersToRemove.add(layer);
			}
		}
		for (Layer layer : layersToRemove) {
			try {
				layer.dispose();
			} catch (Exception e) {
			}
			map.removeLayer(layer);
		}
		
		try {
			MapPane mapPane = frame.getMapPane();
			if (mapPane == null) {
				return;
			}
			ReferencedEnvelope bounds = mapPane.getDisplayArea();
			if (bounds == null) {
				return;
			}
			
			GeometryFactory gf = new GeometryFactory();
			SimpleFeatureType lineType = DataUtilities.createType("Graticule", "geom:LineString");
			DefaultFeatureCollection graticuleCollection = new DefaultFeatureCollection("GraticuleLayer", lineType);
			
			double minLon = bounds.getMinX();
			double maxLon = bounds.getMaxX();
			double minLat = bounds.getMinY();
			double maxLat = bounds.getMaxY();
			
			double lonRange = maxLon - minLon;
			double latRange = maxLat - minLat;
			
			double lonStep = calculateStep(lonRange);
			double latStep = calculateStep(latRange);
			
			for (double lon = Math.ceil(minLon / lonStep) * lonStep; lon <= maxLon; lon += lonStep) {
				Coordinate[] coords = new Coordinate[] {
					new Coordinate(lon, minLat),
					new Coordinate(lon, maxLat)
				};
				LineString line = gf.createLineString(coords);
				SimpleFeature feature = DataUtilities.template(lineType);
				feature.setDefaultGeometry(line);
				graticuleCollection.add(feature);
			}
			
			for (double lat = Math.ceil(minLat / latStep) * latStep; lat <= maxLat; lat += latStep) {
				Coordinate[] coords = new Coordinate[] {
					new Coordinate(minLon, lat),
					new Coordinate(maxLon, lat)
				};
				LineString line = gf.createLineString(coords);
				SimpleFeature feature = DataUtilities.template(lineType);
				feature.setDefaultGeometry(line);
				graticuleCollection.add(feature);
			}
			
			if (!graticuleCollection.isEmpty()) {
				Style graticuleStyle = StyleFactory.createGraticuleStyle();
				FeatureLayer graticuleLayer = new FeatureLayer(graticuleCollection, graticuleStyle);
				graticuleLayer.setTitle("GraticuleLayer");
				map.addLayer(graticuleLayer);
			}
		} catch (Exception e) {
		}
	}
	
	private double calculateStep(double range) {
		if (range <= 0) {
			return 1.0;
		}
		double magnitude = Math.pow(10, Math.floor(Math.log10(range)));
		double normalized = range / magnitude;
		double step;
		if (normalized <= 1.5) {
			step = 0.1 * magnitude;
		} else if (normalized <= 3) {
			step = 0.2 * magnitude;
		} else if (normalized <= 7) {
			step = 0.5 * magnitude;
		} else {
			step = 1.0 * magnitude;
		}
		return step;
	}
	
	private void setupStatusBarExportButton() {
		SwingUtilities.invokeLater(() -> {
			try {
				if (exportImageButton != null && exportImageButton.getParent() != null) {
					exportImageButton.getParent().remove(exportImageButton);
				}
				
				File saveIconFile = com.treloc.xtreloc.app.gui.util.AppDirectoryManager.getSaveIconFile();
				
				if (saveIconFile != null && saveIconFile.exists()) {
					ImageIcon saveIcon = new ImageIcon(saveIconFile.getAbsolutePath());
					Image img = saveIcon.getImage();
					Image scaledImg = img.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
					ImageIcon scaledIcon = new ImageIcon(scaledImg);
					exportImageButton = new JButton(scaledIcon);
					exportImageButton.setToolTipText("Export Map Image");
					exportImageButton.setBorderPainted(false);
					exportImageButton.setContentAreaFilled(false);
					exportImageButton.setFocusPainted(false);
					exportImageButton.addActionListener(e -> exportMapImage());
				} else {
					exportImageButton = new JButton("Save");
					exportImageButton.addActionListener(e -> exportMapImage());
				}
				
				Container contentPane = frame.getContentPane();
				JPanel statusBar = findStatusBarPanel(contentPane);
				
				if (statusBar != null) {
					if (exportImageButton.getParent() != statusBar) {
						if (exportImageButton.getParent() != null) {
							exportImageButton.getParent().remove(exportImageButton);
						}
						LayoutManager layout = statusBar.getLayout();
						if (layout instanceof FlowLayout || layout instanceof BoxLayout) {
							statusBar.add(Box.createHorizontalGlue());
						}
						statusBar.add(exportImageButton);
						statusBar.revalidate();
						statusBar.repaint();
					}
				}
			} catch (Exception e) {
				System.err.println("Failed to setup status bar export button: " + e.getMessage());
				e.printStackTrace();
				if (exportImageButton != null) {
					try {
						if (exportImageButton.getParent() != frame.getToolBar()) {
							frame.getToolBar().add(Box.createHorizontalGlue());
							frame.getToolBar().add(exportImageButton);
							frame.getToolBar().revalidate();
							frame.getToolBar().repaint();
						}
					} catch (Exception ex) {
						System.err.println("Failed to add export button to toolbar: " + ex.getMessage());
					}
				}
			}
		});
	}
	
	/**
	 * Recursively searches for a status bar panel in a container.
	 */
	private JPanel findStatusBarPanel(Container container) {
		for (Component comp : container.getComponents()) {
			if (comp instanceof JPanel) {
				JPanel panel = (JPanel) comp;
				String panelName = panel.getName();
				if (panelName != null && (panelName.contains("Status") || panelName.contains("status"))) {
					return panel;
				}
				for (Component child : panel.getComponents()) {
					if (child instanceof JLabel) {
						return panel;
					}
				}
			}
			if (comp instanceof Container) {
				JPanel found = findStatusBarPanel((Container) comp);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}
	
	/**
	 * Interpolate between two colors.
	 */
	private Color interpolateColor(Color c1, Color c2, float ratio) {
		int r = (int) (c1.getRed() * (1 - ratio) + c2.getRed() * ratio);
		int g = (int) (c1.getGreen() * (1 - ratio) + c2.getGreen() * ratio);
		int b = (int) (c1.getBlue() * (1 - ratio) + c2.getBlue() * ratio);
		return new Color(r, g, b);
	}
	
	private Color viridisColor(float ratio) {
		if (ratio < 0.25f) {
			return interpolateColor(new Color(68, 1, 84), new Color(59, 82, 139), ratio * 4);
		} else if (ratio < 0.5f) {
			return interpolateColor(new Color(59, 82, 139), new Color(33, 144, 140), (ratio - 0.25f) * 4);
		} else if (ratio < 0.75f) {
			return interpolateColor(new Color(33, 144, 140), new Color(92, 200, 99), (ratio - 0.5f) * 4);
		} else {
			return interpolateColor(new Color(92, 200, 99), new Color(253, 231, 37), (ratio - 0.75f) * 4);
		}
	}
	
	private Color plasmaColor(float ratio) {
		if (ratio < 0.25f) {
			return interpolateColor(new Color(13, 8, 135), new Color(75, 3, 161), ratio * 4);
		} else if (ratio < 0.5f) {
			return interpolateColor(new Color(75, 3, 161), new Color(125, 3, 168), (ratio - 0.25f) * 4);
		} else if (ratio < 0.75f) {
			return interpolateColor(new Color(125, 3, 168), new Color(185, 54, 96), (ratio - 0.5f) * 4);
		} else {
			return interpolateColor(new Color(185, 54, 96), new Color(253, 231, 37), (ratio - 0.75f) * 4);
		}
	}
	
	private Color rainbowColor(float ratio) {
		float hue = ratio * 0.7f;
		return Color.getHSBColor(hue, 1.0f, 1.0f);
	}

	public void showStations(List<Station> stations) throws Exception {
		showStations(stations, null, null);
	}
	
	public void showStations(List<Station> stations, String colorColumn, double[] colorValues) throws Exception {
		java.util.List<Layer> layersToRemove = new java.util.ArrayList<>();
		for (Layer layer : map.layers()) {
			if ("StationLayer".equals(layer.getTitle())) {
				layersToRemove.add(layer);
			}
		}
		for (Layer layer : layersToRemove) {
			try {
				layer.dispose();
			} catch (Exception e) {
			}
			map.removeLayer(layer);
		}

		SimpleFeatureType type = DataUtilities.createType("Station", "geom:Point,code:String,lat:Double,lon:Double,dep:Double");
		GeometryFactory gf = new GeometryFactory();
		
		if (colorColumn != null && colorValues != null && colorValues.length == stations.size()) {
			double minValue = Double.MAX_VALUE;
			double maxValue = Double.MIN_VALUE;
			for (double val : colorValues) {
				if (val < minValue) minValue = val;
				if (val > maxValue) maxValue = val;
			}
			
			java.util.Map<Color, DefaultFeatureCollection> colorFeatureCollections = new java.util.HashMap<>();
			
			for (int i = 0; i < stations.size(); i++) {
				Station s = stations.get(i);
				double valueToColor = colorValues[i];
				
				double normalized = (maxValue > minValue) 
					? (valueToColor - minValue) / (maxValue - minValue) 
					: 0.0;
				
				Color color = getColorFromPalette((float) normalized);
				
				DefaultFeatureCollection col = colorFeatureCollections.computeIfAbsent(color, 
					k -> new DefaultFeatureCollection("StationLayer_" + k.getRGB(), type));
				
				SimpleFeature f = DataUtilities.template(type);
				f.setDefaultGeometry(gf.createPoint(new Coordinate(s.getLon(), s.getLat())));
				f.setAttribute("code", s.getCode());
				f.setAttribute("lat", s.getLat());
				f.setAttribute("lon", s.getLon());
				f.setAttribute("dep", s.getDep());
				col.add(f);
			}
			
			for (java.util.Map.Entry<Color, DefaultFeatureCollection> entry : colorFeatureCollections.entrySet()) {
				Color color = entry.getKey();
				DefaultFeatureCollection col = entry.getValue();
				Style style = StyleFactory.createColoredStationStyle(color.getRed(), color.getGreen(), color.getBlue());
				FeatureLayer layer = new FeatureLayer(col, style);
				layer.setTitle("StationLayer");
				map.addLayer(layer);
			}
			
			if (stations.size() > 0) {
				String label = colorColumn;
				setColorRange(minValue, maxValue, label);
			}
		} else {
			DefaultFeatureCollection col = new DefaultFeatureCollection("StationLayer", type);
			for (Station s : stations) {
				SimpleFeature f = DataUtilities.template(type);
				f.setDefaultGeometry(gf.createPoint(new Coordinate(s.getLon(), s.getLat())));
				f.setAttribute("code", s.getCode());
				f.setAttribute("lat", s.getLat());
				f.setAttribute("lon", s.getLon());
				f.setAttribute("dep", s.getDep());
				col.add(f);
			}
			FeatureLayer layer = new FeatureLayer(col, StyleFactory.stationStyle());
			layer.setTitle("StationLayer");
			map.addLayer(layer);
		}
		
		updateLegend();
		updateOverlayPositions();
		forceMapRepaint();
	}
	
	public void highlightPoint(double lon, double lat) throws Exception {
		highlightPoint(lon, lat, null, null, null);
	}
	
	public void highlightPoint(double lon, double lat, String type) {
		highlightPoint(lon, lat, type, null, null);
	}
	
	public void highlightPoint(double lon, double lat, String type, 
			com.treloc.xtreloc.app.gui.model.CatalogInfo.SymbolType symbolType, Color color) {
		clearHighlight();

		try {
			SimpleFeatureType highlightType = DataUtilities.createType("Highlight", "geom:Point");
			DefaultFeatureCollection highlightCollection = new DefaultFeatureCollection("HighlightLayer", highlightType);
			GeometryFactory gf = new GeometryFactory();

			SimpleFeature feature = DataUtilities.template(highlightType);
			feature.setDefaultGeometry(gf.createPoint(new Coordinate(lon, lat)));
			highlightCollection.add(feature);

			Style highlightStyle;
			if ("station".equals(type)) {
				highlightStyle = StyleFactory.selectedPointStyle();
			} else if (symbolType != null && color != null) {
				highlightStyle = StyleFactory.selectedSymbolStyle(symbolType, color);
			} else {
				highlightStyle = StyleFactory.selectedPointStyle();
			}

			FeatureLayer highlightLayer = new FeatureLayer(highlightCollection, highlightStyle);
			highlightLayer.setTitle("Selected");
			map.addLayer(highlightLayer);
		} catch (Exception e) {
			System.err.println("Failed to highlight point: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public void clearHighlight() {
		removeHighlightLayer();
	}
	
	private void removeHighlightLayer() {
		java.util.List<Layer> layersToRemove = new java.util.ArrayList<>();
		for (Layer layer : map.layers()) {
			if ("Selected".equals(layer.getTitle())) {
				layersToRemove.add(layer);
			}
		}
		for (Layer layer : layersToRemove) {
			try {
				layer.dispose();
			} catch (Exception e) {
			}
			map.removeLayer(layer);
		}
	}
	
	/**
	 * Sets the symbol size for map display.
	 * 
	 * @param size the symbol size (5-50)
	 */
	public void setSymbolSize(int size) {
		this.symbolSize = Math.max(5, Math.min(50, size));
		updateLegend();
		forceMapRepaint();
	}
	
	/**
	 * Gets the current symbol size.
	 * 
	 * @return the symbol size
	 */
	public int getSymbolSize() {
		return symbolSize;
	}
	
	/**
	 * Sets the default color palette for map visualization.
	 * This palette will be used when displaying hypocenters on the map.
	 * 
	 * @param palette the color palette to use as default
	 */
	public void setDefaultPalette(ColorPalette palette) {
		this.currentPalette = palette;
		if (colorBarPanel != null) {
			colorBarPanel.setPalette(palette);
		}
		if (colorBarPanel != null) {
			colorBarPanel.setPalette(palette);
		}
		refreshMapColors();
	}
	
	/**
	 * Gets the current color palette.
	 * 
	 * @return the current color palette
	 */
	public ColorPalette getCurrentPalette() {
		return currentPalette;
	}
	
	private void exportMapImage() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export Map as Image");
		fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"PNG files (*.png)", "png"));
		fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg"));
		
		com.treloc.xtreloc.app.gui.util.FileChooserHelper.setDefaultDirectory(fileChooser);
		fileChooser.setSelectedFile(new File("map.png"));
		
		int result = fileChooser.showSaveDialog(frame);
		if (result == JFileChooser.APPROVE_OPTION) {
			File outputFile = fileChooser.getSelectedFile();
			try {
				File savedFile = exportMapImageToFile(outputFile);
				String msg = "Map exported as image: " + savedFile.getAbsolutePath();
				if (!savedFile.equals(outputFile)) {
					msg += "\n(Saved as PNG because JPEG is not available on this system.)";
				}
				JOptionPane.showMessageDialog(frame, msg, "Information", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(frame,
					"Failed to export image: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	/**
	 * Exports the map pane to an image file. Supports PNG and JPEG.
	 * On systems where JPEG ImageIO plugin fails (e.g. some macOS/JDK), JPEG is fallback to PNG automatically.
	 * @param outputFile target file (extension determines format)
	 * @return the file that was actually written (may differ from outputFile if JPEG fallback to PNG occurred)
	 */
	public File exportMapImageToFile(File outputFile) throws Exception {
		Component mapPane = frame.getMapPane();
		BufferedImage image = new BufferedImage(
			mapPane.getWidth(), 
			mapPane.getHeight(), 
			BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		mapPane.paint(g2d);
		g2d.dispose();
		
		String extension = getFileExtension(outputFile.getName()).toLowerCase();
		if ("png".equals(extension)) {
			javax.imageio.ImageIO.write(image, "PNG", outputFile);
			return outputFile;
		} else if ("jpg".equals(extension) || "jpeg".equals(extension)) {
			try {
				javax.imageio.ImageIO.write(image, "JPEG", outputFile);
				return outputFile;
			} catch (Exception jpegEx) {
				// On some macOS/JDK combinations, JPEG ImageIO plugin fails (e.g. ServiceConfigurationError,
				// vendorName == null). Fall back to PNG with the same base name.
				String baseName = outputFile.getName();
				int lastDot = baseName.lastIndexOf('.');
				if (lastDot > 0) {
					baseName = baseName.substring(0, lastDot);
				}
				File pngFile = new File(outputFile.getParent(), baseName + ".png");
				javax.imageio.ImageIO.write(image, "PNG", pngFile);
				return pngFile;
			}
		} else {
			throw new IllegalArgumentException("Unsupported image format. Use PNG or JPEG.");
		}
	}
	
	/**
	 * Gets the file extension from a filename.
	 */
	private String getFileExtension(String filename) {
		int lastDot = filename.lastIndexOf(".");
		if (lastDot > 0 && lastDot < filename.length() - 1) {
			return filename.substring(lastDot + 1);
		}
		return "";
	}
	
	/**
	 * Color bar panel inspired by GeoTools ColorRamp design.
	 * Supports flexible label placement and automatic graduation.
	 */
	public static class ColorBarPanel extends JPanel {
		private double minValue = 0.0;
		private double maxValue = 100.0;
		private String label = "Depth (km)";
		private ColorPalette palette = ColorPalette.BLUE_TO_RED;
		private JPanel colorBarDrawingPanel;
		private RangeChangeListener rangeChangeListener;
		private Runnable paletteChangeCallback;
		private boolean labelsVisible = true;
		private int numLabels = 5;
		private Color[] colorArray;
		private static final int COLOR_ARRAY_SIZE = 256;
		
		public interface RangeChangeListener {
			void onRangeChanged(double min, double max);
		}
		
		public void setRangeChangeListener(RangeChangeListener listener) {
			this.rangeChangeListener = listener;
		}
		
		public void setPaletteChangeCallback(Runnable callback) {
			this.paletteChangeCallback = callback;
		}
		
		private void generateColorArray() {
			colorArray = new Color[COLOR_ARRAY_SIZE];
			for (int i = 0; i < COLOR_ARRAY_SIZE; i++) {
				float ratio = (float) i / (COLOR_ARRAY_SIZE - 1);
				colorArray[i] = getColorFromPalette(ratio);
			}
		}
		
		private Color getColorFromPalette(float ratio) {
			switch (this.palette) {
				case VIRIDIS:
					return viridisColor(ratio);
				case PLASMA:
					return plasmaColor(ratio);
				case COOL_TO_WARM:
					return interpolateColor(new Color(59, 76, 192), new Color(180, 4, 38), ratio);
				case RAINBOW:
					return rainbowColor(ratio);
				case GRAYSCALE:
					int gray = (int) (255 * ratio);
					return new Color(gray, gray, gray);
				case BLUE_TO_RED:
				default:
					return interpolateColor(Color.BLUE, Color.RED, ratio);
			}
		}
		
		private Color interpolateColor(Color c1, Color c2, float ratio) {
			int r = (int) (c1.getRed() * (1 - ratio) + c2.getRed() * ratio);
			int g = (int) (c1.getGreen() * (1 - ratio) + c2.getGreen() * ratio);
			int b = (int) (c1.getBlue() * (1 - ratio) + c2.getBlue() * ratio);
			return new Color(r, g, b);
		}
		
		private Color viridisColor(float ratio) {
			if (ratio < 0.25f) {
				return interpolateColor(new Color(68, 1, 84), new Color(59, 82, 139), ratio * 4);
			} else if (ratio < 0.5f) {
				return interpolateColor(new Color(59, 82, 139), new Color(33, 144, 140), (ratio - 0.25f) * 4);
			} else if (ratio < 0.75f) {
				return interpolateColor(new Color(33, 144, 140), new Color(92, 200, 99), (ratio - 0.5f) * 4);
			} else {
				return interpolateColor(new Color(92, 200, 99), new Color(253, 231, 37), (ratio - 0.75f) * 4);
			}
		}
		
		private Color plasmaColor(float ratio) {
			if (ratio < 0.25f) {
				return interpolateColor(new Color(13, 8, 135), new Color(75, 3, 161), ratio * 4);
			} else if (ratio < 0.5f) {
				return interpolateColor(new Color(75, 3, 161), new Color(125, 3, 168), (ratio - 0.25f) * 4);
			} else if (ratio < 0.75f) {
				return interpolateColor(new Color(125, 3, 168), new Color(185, 54, 96), (ratio - 0.5f) * 4);
			} else {
				return interpolateColor(new Color(185, 54, 96), new Color(253, 231, 37), (ratio - 0.75f) * 4);
			}
		}
		
		private Color rainbowColor(float ratio) {
			float hue = ratio * 0.7f;
			return Color.getHSBColor(hue, 1.0f, 1.0f);
		}
		
		private java.util.List<Double> generateGraduation() {
			java.util.List<Double> graduation = new java.util.ArrayList<>();
			double range = maxValue - minValue;
			if (range <= 0) {
				graduation.add(minValue);
				return graduation;
			}
			
			double step = range / (numLabels - 1);
			for (int i = 0; i < numLabels; i++) {
				graduation.add(minValue + step * i);
			}
			return graduation;
		}
		
		private java.awt.Window dialogParent;
		
		public void setDialogParent(java.awt.Window parent) {
			this.dialogParent = parent;
		}
		
		public ColorBarPanel(ColorPalette palette) {
			this.palette = palette;
			generateColorArray();
			setPreferredSize(new java.awt.Dimension(450, 70));
			setMinimumSize(new java.awt.Dimension(350, 60));
			setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 80));
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
			setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
			
			ColorBarPanel self = this;
			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e) {
					if (e.getClickCount() == 1) {
						self.openSettingsDialog();
					}
				}
			});
			
			colorBarDrawingPanel = new JPanel() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					
					FontMetrics fm = g2d.getFontMetrics();
					java.util.List<Double> graduation = generateGraduation();
					
					int maxLabelWidth = 0;
					java.util.List<String> labelStrings = new java.util.ArrayList<>();
					for (Double value : graduation) {
						String labelStr = formatLabel(value);
						labelStrings.add(labelStr);
						int labelWidth = fm.stringWidth(labelStr);
						if (labelWidth > maxLabelWidth) {
							maxLabelWidth = labelWidth;
						}
					}
					
					int labelPadding = 5;
					int sideMargin = Math.max(maxLabelWidth / 2 + labelPadding, 10);
					
					int width = getWidth() - sideMargin * 2;
					int height = getHeight() - (labelsVisible ? 40 : 10);
					int x = sideMargin;
					int y = 10;
					
					if (colorArray == null || colorArray.length == 0) {
						generateColorArray();
					}
					
					for (int i = 0; i < width; i++) {
						int colorIndex = (int) ((float) i / width * (COLOR_ARRAY_SIZE - 1));
						if (colorIndex >= COLOR_ARRAY_SIZE) {
							colorIndex = COLOR_ARRAY_SIZE - 1;
						}
						g2d.setColor(colorArray[colorIndex]);
						g2d.drawLine(x + i, y, x + i, y + height);
					}
					
					if (labelsVisible && graduation.size() > 0) {
						int labelY = y + height + 20;
						int labelBoxPadding = 3;
						
						for (int i = 0; i < graduation.size(); i++) {
							double value = graduation.get(i);
							String labelStr = labelStrings.get(i);
							double ratio = (value - minValue) / (maxValue - minValue);
							if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
								ratio = 0.0;
							}
							int labelX = (int) (x + ratio * width - fm.stringWidth(labelStr) / 2);
							
							int labelBoxWidth = fm.stringWidth(labelStr) + labelBoxPadding * 2;
							int labelBoxHeight = fm.getHeight() + labelBoxPadding * 2;
							
							g2d.setColor(Color.WHITE);
							g2d.fillRect(labelX - labelBoxPadding, labelY - fm.getAscent() - labelBoxPadding,
								labelBoxWidth, labelBoxHeight);
							g2d.setColor(Color.BLACK);
							g2d.drawRect(labelX - labelBoxPadding, labelY - fm.getAscent() - labelBoxPadding,
								labelBoxWidth, labelBoxHeight);
							g2d.drawString(labelStr, labelX, labelY);
						}
					}
				}
				
				private String formatLabel(double value) {
					double range = maxValue - minValue;
					if (range == 0) {
						return String.format("%.1f", value);
					}
					
					double absMax = Math.max(Math.abs(minValue), Math.abs(maxValue));
					if (absMax >= 1000) {
						return String.format("%.0f", value);
					} else if (absMax >= 100) {
						return String.format("%.1f", value);
					} else if (absMax >= 10) {
						return String.format("%.2f", value);
					} else {
						return String.format("%.3f", value);
					}
				}
			};
			colorBarDrawingPanel.setOpaque(false);
			colorBarDrawingPanel.setPreferredSize(new java.awt.Dimension(400, 40));
			colorBarDrawingPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 50));
			
			add(Box.createVerticalStrut(5));
			add(colorBarDrawingPanel);
			add(Box.createVerticalStrut(5));
		}
		
		public void setRange(double min, double max, String label) {
			this.minValue = min;
			this.maxValue = max;
			this.label = label;
			if (colorBarDrawingPanel != null) {
				colorBarDrawingPanel.repaint();
			}
		}
		
		public void setPalette(ColorPalette palette) {
			this.palette = palette;
			generateColorArray();
			if (paletteChangeCallback != null) {
				paletteChangeCallback.run();
			}
			if (colorBarDrawingPanel != null) {
				colorBarDrawingPanel.repaint();
			}
		}
		
		public void setLabelsVisible(boolean visible) {
			this.labelsVisible = visible;
			if (colorBarDrawingPanel != null) {
				colorBarDrawingPanel.repaint();
			}
		}
		
		public boolean isLabelsVisible() {
			return labelsVisible;
		}
		
		public void setNumLabels(int numLabels) {
			this.numLabels = Math.max(2, Math.min(20, numLabels));
			if (colorBarDrawingPanel != null) {
				colorBarDrawingPanel.repaint();
			}
		}
		
		public int getNumLabels() {
			return numLabels;
		}
		
		public String getLabel() {
			return label;
		}
		
		public double getMinValue() {
			return minValue;
		}
		
		public double getMaxValue() {
			return maxValue;
		}
		
		public ColorPalette getPalette() {
			return palette;
		}
		
		public Color[] getColorArray() {
			return colorArray != null ? colorArray.clone() : null;
		}
		
		private void openSettingsDialog() {
			java.awt.Window parent = dialogParent;
			if (parent == null) {
				parent = SwingUtilities.getWindowAncestor(this);
			}
			ColorMapSettingsDialog dialog = new ColorMapSettingsDialog(parent, this);
			dialog.setVisible(true);
		}
	}
	
	private static class ScaleBarPanel extends JPanel {
		private double scaleBarWidth = 100.0;
		private double displayValue = 1.0;
		private String unit = "km";
		
		public ScaleBarPanel() {
			setOpaque(false);
			setPreferredSize(new java.awt.Dimension(200, 50));
			setMinimumSize(new java.awt.Dimension(150, 40));
			setMaximumSize(new java.awt.Dimension(300, 60));
		}
		
		public void updateScale(double widthPixels, double value, String unit) {
			this.scaleBarWidth = Math.max(50, Math.min(250, widthPixels));
			this.displayValue = value;
			this.unit = unit;
			repaint();
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			
			int panelWidth = getWidth();
			int panelHeight = getHeight();
			int margin = 15;
			
			FontMetrics fm = g2d.getFontMetrics();
			
			int barX = margin;
			int barY = panelHeight - margin - 20;
			int barHeight = 4;
			int tickHeight = 8;
			
			int actualBarWidth = (int) Math.min(scaleBarWidth, panelWidth - 2 * margin);
			
			g2d.setColor(new Color(0, 0, 0, 200));
			g2d.fillRect(barX, barY, actualBarWidth, barHeight);
			
			g2d.setColor(new Color(255, 255, 255, 200));
			g2d.fillRect(barX, barY, actualBarWidth / 2, barHeight);
			
			g2d.setColor(new Color(0, 0, 0, 200));
			g2d.setStroke(new BasicStroke(1.5f));
			g2d.drawRect(barX, barY, actualBarWidth, barHeight);
			
			g2d.drawLine(barX, barY, barX, barY + tickHeight);
			g2d.drawLine(barX + actualBarWidth, barY, barX + actualBarWidth, barY + tickHeight);
			g2d.drawLine(barX + actualBarWidth / 2, barY, barX + actualBarWidth / 2, barY + tickHeight / 2);
			
			String label;
			if (displayValue >= 1.0) {
				label = String.format("%.0f %s", displayValue, unit);
			} else {
				label = String.format("%.2f %s", displayValue, unit);
			}
			
			int labelWidth = fm.stringWidth(label);
			int labelX = barX + (actualBarWidth - labelWidth) / 2;
			int labelY = barY - 8;
			
			g2d.setColor(Color.WHITE);
			g2d.fillRect(labelX - 4, labelY - fm.getAscent() - 3, labelWidth + 8, fm.getHeight() + 6);
			
			g2d.setColor(Color.BLACK);
			g2d.drawString(label, labelX, labelY);
			
			g2d.dispose();
		}
	}
	
}

