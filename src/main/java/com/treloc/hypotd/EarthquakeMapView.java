package com.treloc.hypotd;

// Java standard libraries
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

// Java AWT/Swing
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
// import javax.swing.JFileChooser;

// GeoTools API
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.style.Style;
import org.geotools.api.style.Rule;
import org.geotools.api.style.Symbolizer;
import org.geotools.api.style.TextSymbolizer;
import org.geotools.api.style.Graphic;
import org.geotools.api.style.Mark;
import org.geotools.api.style.FeatureTypeStyle;

// GeoTools Core
import org.geotools.data.DataUtilities;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.grid.Lines;
import org.geotools.grid.ortholine.OrthoLineDef;
import org.geotools.grid.ortholine.LineOrientation;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.styling.StyleBuilder;
import org.geotools.styling.SLD;
import org.geotools.swing.JMapFrame;
import org.geotools.swing.data.JFileDataStoreChooser;

// JTS Geometry
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import javax.swing.JPanel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.Toolkit;
import java.awt.Robot;
import java.awt.Rectangle;

/**
 * A class for displaying earthquake data on a geographic map.
 * This class handles the visualization of earthquake locations, stations,
 * and geographic features using GeoTools.
 */
public class EarthquakeMapView {
	private static final Logger LOGGER = Logger.getLogger(EarthquakeMapView.class.getName());
	private MapContent map;
	private JMapFrame mapFrame;
	private final double[][] stationTable;
	private final String[] codeStrings;
	private final String shapefilePath;
	private javax.swing.JLabel infoLabel;

	/**
	 * Constructs a new EarthquakeMapView with the specified configuration.
	 *
	 * @param appConfig The configuration containing station data, codes, and file paths
	 * @throws Exception If there is an error loading the map data
	 */
	public EarthquakeMapView(ConfigLoader appConfig) throws Exception {
		this.stationTable = appConfig.getStationTable();
		this.codeStrings = appConfig.getCodeStrings();
		String catalogFile = appConfig.getCatalogFile("MAP");

		// Load shapefile
		this.shapefilePath = appConfig.getShapefilePath();
		File file = new File(shapefilePath);
		if (!file.exists()) {
			LOGGER.warning("Shapefile not found: " + shapefilePath + ". Please select a shapefile.");
			file = JFileDataStoreChooser.showOpenFile("shp", null);
		}

		// Create map content
		map = new MapContent();
		map.setTitle(catalogFile);

		addBackgroundLayer(file);

		// Load and process earthquake data
		List<Point> points = loadEarthquakeData(catalogFile);
		if (!points.isEmpty()) {
			addHypocenterLayer(points);
			addStationLayer();
			addGraticules(points);
			setBound(points);
		}

		// Display map with save functionality
		mapFrame = new JMapFrame(map);
		mapFrame.enableToolBar(true);
		mapFrame.enableStatusBar(true);
		mapFrame.setSize(800, 600);

		// Add save button and mouse listener for clipboard copy
		JButton saveButton = new JButton("Copy to Clipboard");
		saveButton.addActionListener(e -> copyToClipboard(mapFrame.getMapPane()));
		mapFrame.getToolBar().addSeparator();
		mapFrame.getToolBar().add(saveButton);

		// Add info label to toolbar
		infoLabel = new javax.swing.JLabel("Click hypocenter for details");
		mapFrame.getToolBar().addSeparator();
		mapFrame.getToolBar().add(infoLabel);

		// Add mouse listener for hypocenter information
		mapFrame.getMapPane().addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				handleMapClick(e.getPoint());
			}
		});

		mapFrame.setVisible(true);
	}

	/**
	 * Adds a shapefile layer to the map content.
	 *
	 * @param map The MapContent to add the layer to
	 * @param file The shapefile to load
	 * @throws Exception If there is an error loading the shapefile
	 */
	private static void addShapefileLayer(MapContent map, File file) throws Exception {
		FileDataStore store = FileDataStoreFinder.getDataStore(file);
		SimpleFeatureSource featureSource = store.getFeatureSource();
		Style style = SLD.createSimpleStyle(featureSource.getSchema());
		Layer layer = new FeatureLayer(featureSource, style);
		map.addLayer(layer);
	}

	/**
	 * Loads earthquake data from a catalog file.
	 *
	 * @param catalogFile Path to the earthquake catalog file
	 * @return List of Point objects
	 */
	private List<Point> loadEarthquakeData(String catalogFile) {
		List<Point> points = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(catalogFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split("\\s+");
				Point point = new Point();
				point.setLat(Double.parseDouble(parts[1]));
				point.setLon(Double.parseDouble(parts[2]));
				point.setDep(Double.parseDouble(parts[3]));
				point.setTime(parts[0]);
				points.add(point);
			}
		} catch (Exception e) {
			LOGGER.warning("Error loading point data: " + e.getMessage());
		}
		return points;
	}

	/**
	 * Adds a layer displaying seismic stations to the map.
	 */
	private void addStationLayer() {
		try {
			SimpleFeatureType type = DataUtilities.createType("Station", "geometry:Point,code:String");
			DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();

			for (int i = 0; i < stationTable.length; i++) {
				SimpleFeature feature = DataUtilities.template(type);
				feature.setDefaultGeometry(
						new GeometryFactory().createPoint(
							new Coordinate(stationTable[i][1], stationTable[i][0])));
				feature.setAttribute("code", codeStrings[i]);
				featureCollection.add(feature);
			}

			StyleBuilder styleBuilder = new StyleBuilder();
			Style style = createStationStyle(styleBuilder);
			map.addLayer(new FeatureLayer(featureCollection, style));

		} catch (Exception e) {
			LOGGER.warning("Error adding station layer: " + e.getMessage());
		}
	}

	/**
	 * Creates a style for station markers and labels.
	 *
	 * @param styleBuilder The StyleBuilder to use for creating the style
	 * @return Style object for stations
	 */
	private Style createStationStyle(StyleBuilder styleBuilder) {
		// Create point symbolizer
		Mark mark = styleBuilder.createMark(StyleBuilder.MARK_TRIANGLE);
		mark.setFill(styleBuilder.createFill(Color.RED));
		mark.setStroke(styleBuilder.createStroke(Color.BLACK, 1.0));

		Graphic graphic = styleBuilder.createGraphic(null,
				new Mark[] { mark }, null,
				1.0,		// opacity
				10.0,			// size
				0.0);		// rotation

		Symbolizer point = styleBuilder.createPointSymbolizer(graphic);

		// Create text symbolizer
		TextSymbolizer text = styleBuilder.createTextSymbolizer();
		text.setLabel(styleBuilder.attributeExpression("code"));
		
		// Position label above the point
		text.setLabelPlacement(styleBuilder.createPointPlacement(
			styleBuilder.createAnchorPoint(0.5, 0.0),    // Center top
			styleBuilder.createDisplacement(0, 5),		// 5 pixels above
			styleBuilder.literalExpression(0.0)));	// No rotation
		
		// Set label style
		text.setFont(styleBuilder.createFont("SansSerif", 10));
		text.setFill(styleBuilder.createFill(Color.BLACK));
		text.setHalo(styleBuilder.createHalo(Color.WHITE, 1));

		// Combine point and text symbolizers in a single style
		Rule rule = styleBuilder.createRule();
		rule.symbolizers().add(point);
		rule.symbolizers().add(text);
		
		FeatureTypeStyle fts = styleBuilder.createFeatureTypeStyle(point);
		fts.rules().add(rule);
		
		Style style = styleBuilder.createStyle();
		style.featureTypeStyles().add(fts);

		return style;
	}

	/**
	 * Adds a layer displaying earthquake hypocenters to the map.
	 *
	 * @param points List of earthquake data to display
	 */
	private void addHypocenterLayer(List<Point> points) {
		try {
			SimpleFeatureType type = DataUtilities.createType("Hypocenter", "geometry:Point,time:String,depth:Double");
			DefaultFeatureCollection featureCollection = new DefaultFeatureCollection();

			for (Point point : points) {
				SimpleFeature feature = DataUtilities.template(type);
				feature.setDefaultGeometry(
						new GeometryFactory().createPoint(
							new Coordinate(point.getLon(), point.getLat())));
				feature.setAttribute("time", point.getTime());
				feature.setAttribute("depth", point.getDep());
				featureCollection.add(feature);
			}

			StyleBuilder styleBuilder = new StyleBuilder();
			Style style = createHypocenterStyle(styleBuilder);
			map.addLayer(new FeatureLayer(featureCollection, style));

		} catch (Exception e) {
			LOGGER.warning("Error adding hypocenter layer: " + e.getMessage());
		}
	}

	/**
	 * Creates a style for hypocenter markers.
	 *
	 * @param styleBuilder The StyleBuilder to use for creating the style
	 * @return Style object for hypocenters
	 */
	private Style createHypocenterStyle(StyleBuilder styleBuilder) {
		// Create point symbolizer
		Mark mark = styleBuilder.createMark(StyleBuilder.MARK_CIRCLE);
		mark.setFill(styleBuilder.createFill(Color.BLUE));
		mark.setStroke(styleBuilder.createStroke(Color.BLACK, 1.0));

		Graphic graphic = styleBuilder.createGraphic(null,
				new Mark[] { mark }, null,
				1.0, // opacity
				10.0, // size
				0.0); // rotation

		Symbolizer point = styleBuilder.createPointSymbolizer(graphic);

		// Combine point and text symbolizers in a single style
		Rule rule = styleBuilder.createRule();
		rule.symbolizers().add(point);

		FeatureTypeStyle fts = styleBuilder.createFeatureTypeStyle(point);
		fts.rules().add(rule);

		Style style = styleBuilder.createStyle();
		style.featureTypeStyles().add(fts);

		return style;
	}

	/**
	 * Sets the map bounds based on earthquake locations with margins.
	 *
	 * @param points List of earthquakes to determine bounds from
	 */
	private void setBound(List<Point> points) {
		double minLat = points.stream().mapToDouble(Point::getLat).min().orElse(0);
		double maxLat = points.stream().mapToDouble(Point::getLat).max().orElse(0);
		double minLon = points.stream().mapToDouble(Point::getLon).min().orElse(0);
		double maxLon = points.stream().mapToDouble(Point::getLon).max().orElse(0);

		double minRange = 0.1;
		if (Math.abs(maxLat - minLat) < minRange) {
			double midLat = (maxLat + minLat) / 2;
			minLat = midLat - minRange / 2;
			maxLat = midLat + minRange / 2;
		}
		if (Math.abs(maxLon - minLon) < minRange) {
			double midLon = (maxLon + minLon) / 2;
			minLon = midLon - minRange / 2;
			maxLon = midLon + minRange / 2;
		}

		double latMargin = Math.max((maxLat - minLat) * 0.1, 0.01);
		double lonMargin = Math.max((maxLon - minLon) * 0.1, 0.01);

		ReferencedEnvelope bounds = new ReferencedEnvelope(
				minLon - lonMargin,
				maxLon + lonMargin,
				minLat - latMargin,
				maxLat + latMargin,
				DefaultGeographicCRS.WGS84);

		map.getViewport().setBounds(bounds);
	}

	/**
	 * Adds graticules (grid lines) to the map.
	 *
	 * @param points List of earthquakes to determine grid extent
	 */
	private void addGraticules(List<Point> points) {
		try {
			double minLat = points.stream().mapToDouble(Point::getLat).min().orElse(0);
			double maxLat = points.stream().mapToDouble(Point::getLat).max().orElse(0);
			double minLon = points.stream().mapToDouble(Point::getLon).min().orElse(0);
			double maxLon = points.stream().mapToDouble(Point::getLon).max().orElse(0);

			ReferencedEnvelope bounds = new ReferencedEnvelope(
					minLon - 3,
					maxLon + 3,
					minLat - 3,
					maxLat + 3,
					DefaultGeographicCRS.WGS84);

			List<OrthoLineDef> lineDefs = Arrays.asList(
					new OrthoLineDef(LineOrientation.VERTICAL, 2, 1.0),
					new OrthoLineDef(LineOrientation.HORIZONTAL, 2, 1.0));

			SimpleFeatureSource grid = Lines.createOrthoLines(bounds, lineDefs, 0.1);
			StyleBuilder sb = new StyleBuilder();
			Style style = sb.createStyle(sb.createLineSymbolizer(Color.BLACK, 0.25));

			map.addLayer(new FeatureLayer(grid, style));
		} catch (Exception e) {
			LOGGER.warning("Error adding graticules: " + e.getMessage());
		}
	}

	/**
	 * Copies the current map view to the system clipboard as an image.
	 *
	 * @param panel The JPanel containing the map to copy
	 */
	private void copyToClipboard(JPanel panel) {
		try {
			int width = panel.getWidth();
			int height = panel.getHeight();

			if (width <= 0 || height <= 0) {
				LOGGER.warning("Error: Panel size is invalid");
				return;
			}

			// Get the screenshot
			Rectangle bounds = panel.getBounds();
			bounds.setLocation(panel.getLocationOnScreen());
			Robot robot = new Robot();
			final BufferedImage image = robot.createScreenCapture(bounds);

			// Copy the image to the clipboard
			Transferable transferable = new Transferable() {
				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[] { DataFlavor.imageFlavor };
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) {
					return DataFlavor.imageFlavor.equals(flavor);
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
					if (!isDataFlavorSupported(flavor)) {
						throw new UnsupportedFlavorException(flavor);
					}
					return image;
				}
			};

			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
			LOGGER.info("Map copied to clipboard");
		} catch (Exception e) {
			LOGGER.warning("Error copying to clipboard: " + e.getMessage());
		}
	}

	/**
	 * Handles mouse clicks on the map and displays hypocenter information.
	 *
	 * @param clickPoint The point where the mouse was clicked
	 */
	private void handleMapClick(java.awt.Point clickPoint) {
		try {
			// Convert screen coordinates to map coordinates
			java.awt.geom.Point2D mapPoint = mapFrame.getMapPane().getScreenToWorldTransform().transform(clickPoint, null);

			// Create a small search area around the clicked point
			double searchRadius = 0.1; // degrees
			org.locationtech.jts.geom.Coordinate clickCoord = 
				new org.locationtech.jts.geom.Coordinate(mapPoint.getX(), mapPoint.getY());
			
			// Search through all layers for the hypocenter layer
			for (Layer layer : map.layers()) {
				if (layer instanceof FeatureLayer) {
					FeatureLayer featureLayer = (FeatureLayer) layer;
					if (featureLayer.getFeatureSource().getSchema().getName().getLocalPart().equals("Hypocenter")) {
						FeatureCollection<?, ?> features = 
							featureLayer.getFeatureSource().getFeatures();
						
						// Find the closest feature
						double minDistance = Double.MAX_VALUE;
						String closestFeatureInfo = null;
						
						for (Object obj : features.toArray()) {
							SimpleFeature feature = (SimpleFeature) obj;
							org.locationtech.jts.geom.Geometry geom = 
								(org.locationtech.jts.geom.Geometry) feature.getDefaultGeometry();
							double distance = geom.getCoordinate().distance(clickCoord);
							
							if (distance < minDistance && distance < searchRadius) {
								minDistance = distance;
								String time = (String) feature.getProperty("time").getValue();
								Double depth = (Double) feature.getProperty("depth").getValue();
								closestFeatureInfo = String.format("Time: %s, Depth: %.1f km", 
									time, depth);
							}
						}
						
						// Update the info label
						if (closestFeatureInfo != null) {
							infoLabel.setText(closestFeatureInfo);
						} else {
							infoLabel.setText("No hypocenter found at clicked location");
						}
						
						return;
					}
				}
			}
		} catch (Exception ex) {
			LOGGER.warning("Error handling map click: " + ex.getMessage());
			infoLabel.setText("Error getting hypocenter information");
		}
	}

	/**
	 * Common error handling
	 *
	 * @param operation Description of the operation in progress
	 * @param e The exception that occurred
	 */
	private void handleError(String operation, Exception e) {
		LOGGER.warning(String.format("Error %s: %s", operation, e.getMessage()));
	}

	private void addBackgroundLayer(File file) {
		try {
			if (!file.exists()) {
				LOGGER.warning("File not found: " + file.getPath());
				file = JFileDataStoreChooser.showOpenFile("shp", null);
				if (file == null) {
					LOGGER.warning("No file selected, continuing without background map");
					return;
				}
			}

			String fileName = file.getName().toLowerCase();
			LOGGER.info("Processing file: " + fileName);
			
			if (fileName.endsWith(".shp")) {
				addShapefileLayer(map, file);
			} else {
				LOGGER.warning("Unsupported file format: " + fileName);
			}
		} catch (Exception e) {
			LOGGER.severe("Error in addBackgroundLayer: " + e.getMessage());
			e.printStackTrace();
			handleError("loading background layer", e);
		}
	}
}
