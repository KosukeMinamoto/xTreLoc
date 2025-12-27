package com.treloc.xtreloc.app.gui.view;

import com.treloc.xtreloc.app.gui.model.*;
import com.treloc.xtreloc.app.gui.service.StyleFactory;
import com.treloc.xtreloc.io.Station;

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

public class MapView {

	private final MapContent map = new MapContent();
	private final JMapFrame frame = new JMapFrame(map);
	// private JLabel info = new JLabel("緯度経度: --");
	private JLabel coordLabel = new JLabel(" ");
	private ColorBarPanel colorBarPanel;
	private JComboBox<ColorPalette> paletteCombo;
	private double minDepth = 0.0;
	private double maxDepth = 100.0;
	private double minColorValue = 0.0;
	private double maxColorValue = 100.0;
	private int symbolSize = 10;
	
	private java.util.List<Hypocenter> lastHypocenters = null;
	private String lastColorColumn = null;
	private double[] lastColorValues = null;
	
	private JButton exportImageButton;
	
	/**
	 * Color palette types
	 */
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
		
		// マップペインの背景色をテーマから取得
		updateMapBackground();
		
		// ツールバーにロゴアイコンを追加
		try {
			File logoFile = com.treloc.xtreloc.app.gui.util.AppDirectoryManager.getLogoFile();
			if (logoFile.exists()) {
				ImageIcon logoIcon = new ImageIcon(logoFile.getAbsolutePath());
				// アイコンサイズを調整（ステータスバー用に小さく）
				Image img = logoIcon.getImage();
				Image scaledImg = img.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
				ImageIcon scaledIcon = new ImageIcon(scaledImg);
				JLabel logoLabel = new JLabel(scaledIcon);
				// ツールバーの先頭に追加
				frame.getToolBar().add(logoLabel, 0);
			}
		} catch (Exception e) {
			System.err.println("ロゴの読み込みに失敗: " + e.getMessage());
		}
		
		// ツールバーに情報ラベルを追加
		// frame.getToolBar().add(info);
		frame.getToolBar().add(Box.createHorizontalStrut(10));
		frame.getToolBar().add(coordLabel);
		
		// Store palette combo reference (will be added to color bar panel)
		this.paletteCombo = null; // Will be set in ColorBarPanel
		
		// カラーバーパネルを作成（マップ内に表示）
		colorBarPanel = new ColorBarPanel(currentPalette);
		
		// Add color bar as overlay on the map pane
		setupMapOverlays();
		
		// 画像出力ボタンをステータスバーの右上に追加
		setupStatusBarExportButton();
		
		// マウスイベントリスナーを追加
		setupMouseListener();
		
		// グリッド線は表示しない（削除）
		// addGridLayer();
		
		// フレームは親コンテナで統合されるため、ここでは表示しない
		frame.setVisible(false);
	}
	
	/**
	 * マップペインの背景色をテーマから取得して設定
	 */
	private void updateMapBackground() {
		try {
			MapPane mapPane = frame.getMapPane();
			if (mapPane != null) {
				Component mapPaneComponent = (Component) mapPane;
				// テーマから背景色を取得
				Color bgColor = UIManager.getColor("Panel.background");
				if (bgColor != null) {
					mapPaneComponent.setBackground(bgColor);
				} else {
					mapPaneComponent.setBackground(Color.WHITE);
				}
			}
			
			// フレームのコンテンツペインの背景色も設定
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
			System.err.println("マップ背景色の設定に失敗: " + e.getMessage());
		}
	}
	
	/**
	 * テーマ変更時にマップ背景を更新
	 */
	public void updateTheme() {
		updateMapBackground();
		if (frame != null) {
			frame.repaint();
		}
	}
	
	public JMapFrame getFrame() {
		return frame;
	}
	
	/**
	 * 緯度経度のグリッド線を追加
	 */
	private void addGridLayer() {
		try {
			DefaultFeatureCollection gridCollection = new DefaultFeatureCollection();
			SimpleFeatureType gridType = DataUtilities.createType("Grid", 
				"geom:LineString");
			GeometryFactory gf = new GeometryFactory();
			
			// デフォルトの範囲（日本周辺）
			double minLon = 120.0;
			double maxLon = 150.0;
			double minLat = 20.0;
			double maxLat = 50.0;
			
			// 経度線（縦線）- 1度間隔
			for (double lon = Math.ceil(minLon); lon <= maxLon; lon += 1.0) {
				Coordinate[] coords = {
					new Coordinate(lon, minLat),
					new Coordinate(lon, maxLat)
				};
				SimpleFeature f = DataUtilities.template(gridType);
				f.setDefaultGeometry(gf.createLineString(coords));
				gridCollection.add(f);
			}
			
			// 緯度線（横線）- 1度間隔
			for (double lat = Math.ceil(minLat); lat <= maxLat; lat += 1.0) {
				Coordinate[] coords = {
					new Coordinate(minLon, lat),
					new Coordinate(maxLon, lat)
				};
				SimpleFeature f = DataUtilities.template(gridType);
				f.setDefaultGeometry(gf.createLineString(coords));
				gridCollection.add(f);
			}
			
			map.addLayer(new FeatureLayer(gridCollection, StyleFactory.createGridStyle()));
		} catch (Exception e) {
			System.err.println("グリッド線の追加に失敗: " + e.getMessage());
		}
	}
	
	private void setupMouseListener() {
		frame.getMapPane().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				updateCoordinates(e);
			}
		});
		
		frame.getMapPane().addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				updateCoordinates(e);
			}
		});
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
				coordLabel.setText(String.format("経度: %s, 緯度: %s", df.format(xCoord), df.format(yCoord)));
			}
		} catch (Exception ex) {
			coordLabel.setText("座標取得エラー");
		}
	}
	
	
	public void setDepthRange(double min, double max) {
		setColorRange(min, max, "深度 (km)");
	}
	
	public void setColorRange(double min, double max, String label) {
		this.minDepth = min;
		this.maxDepth = max;
		this.minColorValue = min;
		this.maxColorValue = max;
		if (colorBarPanel != null) {
			colorBarPanel.setRange(min, max, label);
		}
	}

	/**
	 * Shapefileを追加（複数対応）
	 * @param shp Shapefile
	 * @return レイヤータイトル（表示/非表示の切り替えに使用）
	 */
	public String addShapefile(File shp) throws Exception {
		var store = org.geotools.api.data.FileDataStoreFinder.getDataStore(shp);
		String layerTitle = "ShapefileLayer_" + shp.getName() + "_" + System.currentTimeMillis();
		// Use custom style with thicker line width
		Style shapefileStyle = StyleFactory.createShapefileStyle();
		FeatureLayer layer = new FeatureLayer(store.getFeatureSource(), shapefileStyle);
		layer.setTitle(layerTitle);
		map.addLayer(layer);
		return layerTitle;
	}
	
	/**
	 * Shapefileレイヤーの表示/非表示を切り替え
	 * @param layerTitle レイヤータイトル
	 * @param visible 表示する場合はtrue
	 */
	public void setShapefileLayerVisibility(String layerTitle, boolean visible) {
		for (Layer layer : map.layers()) {
			if (layerTitle.equals(layer.getTitle())) {
				layer.setVisible(visible);
				// マップを再描画（エラーハンドリングを追加）
				try {
					SwingUtilities.invokeLater(() -> {
						try {
							frame.getMapPane().repaint();
						} catch (Exception e) {
							// GeoToolsのレンダリングエラーを抑制（NullPointerExceptionなど）
							if (e instanceof NullPointerException && 
								(e.getMessage() == null || e.getMessage().contains("loops"))) {
								// GeoToolsの既知のバグを無視
								return;
							}
							// その他のエラーはログに記録
							java.util.logging.Logger.getLogger(MapView.class.getName())
								.warning("マップの再描画に失敗: " + e.getMessage());
						}
					});
				} catch (Exception e) {
					// エラーを無視（GeoToolsの既知のバグ）
					java.util.logging.Logger.getLogger(MapView.class.getName())
						.warning("マップ更新のスケジューリングに失敗: " + e.getMessage());
				}
				break;
			}
		}
	}
	
	/**
	 * Shapefileレイヤーを削除
	 * @param layerTitle レイヤータイトル
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
				// Ignore dispose errors
			}
			map.removeLayer(layer);
		}
		// マップを再描画（エラーハンドリングを追加）
		try {
			SwingUtilities.invokeLater(() -> {
				try {
					frame.getMapPane().repaint();
				} catch (Exception e) {
					// GeoToolsのレンダリングエラーを抑制（NullPointerExceptionなど）
					if (e instanceof NullPointerException && 
						(e.getMessage() == null || e.getMessage().contains("loops"))) {
						// GeoToolsの既知のバグを無視
						return;
					}
					// その他のエラーはログに記録
					java.util.logging.Logger.getLogger(MapView.class.getName())
						.warning("マップの再描画に失敗: " + e.getMessage());
				}
			});
		} catch (Exception e) {
			// エラーを無視（GeoToolsの既知のバグ）
			java.util.logging.Logger.getLogger(MapView.class.getName())
				.warning("マップ更新のスケジューリングに失敗: " + e.getMessage());
		}
	}

	public void showHypocenters(List<Hypocenter> hypos) throws Exception {
		showHypocenters(hypos, null, null);
	}
	
	public void showHypocenters(List<Hypocenter> hypos, String colorColumn, double[] colorValues) throws Exception {
		// Store hypocenters for palette refresh
		lastHypocenters = hypos;
		lastColorColumn = colorColumn;
		lastColorValues = colorValues;
		
		// 既存のHypoレイヤーとエラーバーレイヤーを削除（disposeを呼び出してメモリリークを防ぐ）
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
				// Ignore dispose errors
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
		
		// 色を計算
		for (int i = 0; i < hypos.size(); i++) {
			double colorValue = (colorValues != null && i < colorValues.length) 
				? colorValues[i] 
				: hypos.get(i).depth; // デフォルトは深度
			
			if (colorValue < minValue) minValue = colorValue;
			if (colorValue > maxValue) maxValue = colorValue;
		}
		
		// 各ポイントの色を計算
		for (int i = 0; i < hypos.size(); i++) {
			double colorValue = (colorValues != null && i < colorValues.length) 
				? colorValues[i] 
				: hypos.get(i).depth;
			
			// 正規化（0-1の範囲）
			double normalized = (maxValue > minValue) 
				? (colorValue - minValue) / (maxValue - minValue) 
				: 0.0;
			
			// Apply color palette
			Color color = getColorFromPalette((float) normalized);
			colors.add(color);
		}
		
		// カラーバーを更新
		String label = (colorColumn != null) ? colorColumn : "深度 (km)";
		setColorRange(minValue, maxValue, label);
		
		// 色ごとにFeatureCollectionを分割してレイヤーを作成
		// パフォーマンスのため、色をグループ化（同じ色のポイントをまとめる）
		java.util.Map<Color, DefaultFeatureCollection> colorCollections = new java.util.HashMap<>();
		
		// エラーバー用のFeatureCollection
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
			
			// エラーバーを追加（xerr, yerrが有効な場合）
			if (h.xerr > 0 || h.yerr > 0) {
				org.locationtech.jts.geom.Polygon errorEllipse = createErrorEllipse(h.lon, h.lat, h.xerr, h.yerr, gf);
				if (errorEllipse != null) {
					SimpleFeature errorFeature = DataUtilities.template(errorBarType);
					errorFeature.setDefaultGeometry(errorEllipse);
					errorBarCollection.add(errorFeature);
				}
			}
		}
		
		// エラーバーレイヤーを追加（震源ポイントの下に表示）
		if (!errorBarCollection.isEmpty()) {
			Style errorBarStyle = StyleFactory.createErrorBarStyle();
			FeatureLayer errorBarLayer = new FeatureLayer(errorBarCollection, errorBarStyle);
			errorBarLayer.setTitle("ErrorBarLayer");
			map.addLayer(errorBarLayer);
		}
		
		// 各色ごとにレイヤーを作成
		for (java.util.Map.Entry<Color, DefaultFeatureCollection> entry : colorCollections.entrySet()) {
			Color color = entry.getKey();
			DefaultFeatureCollection col = entry.getValue();
			Style style = StyleFactory.createColorStyle(color.getRed(), color.getGreen(), color.getBlue());
			FeatureLayer layer = new FeatureLayer(col, style);
			layer.setTitle("HypoLayer_" + color.hashCode());
			map.addLayer(layer);
		}
		
		// マップを再描画（エラーハンドリングを追加）
		try {
			SwingUtilities.invokeLater(() -> {
				try {
					frame.getMapPane().repaint();
				} catch (Exception e) {
					// GeoToolsのレンダリングエラーを抑制（NullPointerExceptionなど）
					if (e instanceof NullPointerException && 
						(e.getMessage() == null || e.getMessage().contains("loops"))) {
						// GeoToolsの既知のバグを無視
						return;
					}
					// その他のエラーはログに記録
					java.util.logging.Logger.getLogger(MapView.class.getName())
						.warning("マップの再描画に失敗: " + e.getMessage());
				}
			});
		} catch (Exception e) {
			// エラーを無視（GeoToolsの既知のバグ）
			java.util.logging.Logger.getLogger(MapView.class.getName())
				.warning("マップ更新のスケジューリングに失敗: " + e.getMessage());
		}
	}
	
	/**
	 * エラー楕円を作成
	 * @param lon 経度
	 * @param lat 緯度
	 * @param xerr x方向誤差 (km)
	 * @param yerr y方向誤差 (km)
	 * @param gf GeometryFactory
	 * @return エラー楕円のPolygon
	 */
	private org.locationtech.jts.geom.Polygon createErrorEllipse(double lon, double lat, double xerr, double yerr, GeometryFactory gf) {
		// 緯度・経度の変換係数（km -> degree）
		// 緯度1度 ≈ 111.32 km
		// 経度1度 ≈ 111.32 km × cos(緯度)
		final double DEG2KM = 111.32;
		double latRad = Math.toRadians(lat);
		double lonErrDeg = xerr / (DEG2KM * Math.cos(latRad));
		double latErrDeg = yerr / DEG2KM;
		
		// 楕円のパラメータ
		int numPoints = 32; // 楕円の精度（点の数）
		Coordinate[] coords = new Coordinate[numPoints + 1];
		
		// 最初の点を計算
		double angle0 = 0.0;
		double x0 = lonErrDeg * Math.cos(angle0);
		double y0 = latErrDeg * Math.sin(angle0);
		coords[0] = new Coordinate(lon + x0, lat + y0);
		
		// 中間の点を計算
		for (int i = 1; i < numPoints; i++) {
			double angle = 2.0 * Math.PI * i / numPoints;
			double x = lonErrDeg * Math.cos(angle);
			double y = latErrDeg * Math.sin(angle);
			coords[i] = new Coordinate(lon + x, lat + y);
		}
		
		// 最後の点は最初の点と同じ座標にする（LinearRingを閉じる）
		coords[numPoints] = new Coordinate(coords[0]);
		
		// 閉じたLinearRingを作成
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
	
	/**
	 * Refresh map colors when palette changes.
	 */
	private void refreshMapColors() {
		// Re-display hypocenters with new palette if they were previously displayed
		if (lastHypocenters != null && !lastHypocenters.isEmpty()) {
			try {
				showHypocenters(lastHypocenters, lastColorColumn, lastColorValues);
			} catch (Exception e) {
				// If re-display fails, just repaint
				frame.getMapPane().repaint();
			}
		} else {
			// Trigger repaint to update colors with new palette
			frame.getMapPane().repaint();
		}
	}
	
	/**
	 * Setup map overlays (color bar with palette selector).
	 */
	private void setupMapOverlays() {
		// Wait for frame to be fully initialized before adding overlays
		SwingUtilities.invokeLater(() -> {
			try {
				// Get the map pane component
				Component mapPaneComponent = (Component) frame.getMapPane();
				if (mapPaneComponent == null) {
					// Retry after a short delay
					Thread.sleep(200);
					mapPaneComponent = (Component) frame.getMapPane();
				}
				
				if (mapPaneComponent != null) {
					Container mapPaneParent = mapPaneComponent.getParent();
					
					// Color bar (top-right corner) with palette selector
					colorBarPanel.setOpaque(true);
					colorBarPanel.setBackground(new Color(255, 255, 255, 240)); // Semi-transparent white background
					colorBarPanel.setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createLineBorder(Color.BLACK, 1),
						BorderFactory.createEmptyBorder(5, 5, 5, 5)));
					
					// Add palette selector to color bar panel
					JComboBox<ColorPalette> paletteCombo = new JComboBox<>(ColorPalette.values());
					paletteCombo.setSelectedItem(currentPalette);
					paletteCombo.addActionListener(e -> {
						currentPalette = (ColorPalette) paletteCombo.getSelectedItem();
						if (colorBarPanel != null) {
							colorBarPanel.setPalette(currentPalette);
						}
						// Refresh map if hypocenters are displayed
						refreshMapColors();
					});
					this.paletteCombo = paletteCombo;
					colorBarPanel.addPaletteSelector(paletteCombo);
					
					// Add overlay components to map pane's parent without changing layout
					if (mapPaneParent != null) {
						// Add component with absolute positioning
						mapPaneParent.add(colorBarPanel);
						colorBarPanel.setLocation(0, 0);
					}
					
					// Update component positions when map pane is resized
					mapPaneComponent.addComponentListener(new java.awt.event.ComponentAdapter() {
						@Override
						public void componentResized(java.awt.event.ComponentEvent e) {
							updateOverlayPositions();
						}
					});
					
					// Initial position update
					updateOverlayPositions();
				}
			} catch (Exception e) {
				// If overlay setup fails, just log and continue
				System.err.println("Failed to setup map overlays: " + e.getMessage());
			}
		});
	}
	
	/**
	 * Update positions of overlay components (color bar).
	 */
	private void updateOverlayPositions() {
		Component mapPaneComponent = (Component) frame.getMapPane();
		int mapWidth = mapPaneComponent.getWidth();
		int mapHeight = mapPaneComponent.getHeight();
		
		if (mapWidth > 0 && mapHeight > 0) {
			// Color bar: top-right corner
			if (colorBarPanel != null) {
				int colorBarWidth = 450; // Increased width to accommodate palette selector side by side
				int colorBarHeight = 100; // Increased height to prevent clipping
				colorBarPanel.setBounds(mapWidth - colorBarWidth - 10, 10, colorBarWidth, colorBarHeight);
			}
		}
	}
	
	/**
	 * ステータスバーの右上に画像出力ボタンを追加
	 */
	private void setupStatusBarExportButton() {
		SwingUtilities.invokeLater(() -> {
			try {
				// Create image export button
				try {
					File saveIconFile = new File("save.png");
					if (saveIconFile.exists()) {
						ImageIcon saveIcon = new ImageIcon(saveIconFile.getAbsolutePath());
						Image img = saveIcon.getImage();
						Image scaledImg = img.getScaledInstance(20, 20, Image.SCALE_SMOOTH);
						ImageIcon scaledIcon = new ImageIcon(scaledImg);
						exportImageButton = new JButton(scaledIcon);
						exportImageButton.setToolTipText("画像出力");
						exportImageButton.setBorderPainted(false);
						exportImageButton.setContentAreaFilled(false);
						exportImageButton.setFocusPainted(false);
						exportImageButton.addActionListener(e -> exportMapImage());
					} else {
						// save.pngが見つからない場合はテキストボタン
						exportImageButton = new JButton("保存");
						exportImageButton.addActionListener(e -> exportMapImage());
					}
				} catch (Exception e) {
					// エラー時はテキストボタン
					exportImageButton = new JButton("保存");
					exportImageButton.addActionListener(ev -> exportMapImage());
				}
				
				// ステータスバーを取得
				Container contentPane = frame.getContentPane();
				JPanel statusBar = findStatusBarPanel(contentPane);
				
				if (statusBar != null) {
					// ステータスバーのレイアウトを確認（通常はFlowLayoutまたはBoxLayout）
					LayoutManager layout = statusBar.getLayout();
					if (layout instanceof FlowLayout) {
						// FlowLayoutの場合は右寄せにするため、Glueを追加
						statusBar.add(Box.createHorizontalGlue());
					} else if (layout instanceof BoxLayout) {
						// BoxLayoutの場合はGlueを追加
						statusBar.add(Box.createHorizontalGlue());
					}
					
					// ボタンをステータスバーに追加
					statusBar.add(exportImageButton);
					statusBar.revalidate();
					statusBar.repaint();
				} else {
					// ステータスバーが見つからない場合はツールバーに追加
					frame.getToolBar().add(Box.createHorizontalGlue());
					frame.getToolBar().add(exportImageButton);
				}
			} catch (Exception e) {
				System.err.println("Failed to setup status bar export button: " + e.getMessage());
				// フォールバック: ツールバーに追加
				if (exportImageButton != null) {
					frame.getToolBar().add(Box.createHorizontalGlue());
					frame.getToolBar().add(exportImageButton);
				}
			}
		});
	}
	
	/**
	 * コンテナからステータスバーパネルを再帰的に検索
	 */
	private JPanel findStatusBarPanel(Container container) {
		for (Component comp : container.getComponents()) {
			if (comp instanceof JPanel) {
				JPanel panel = (JPanel) comp;
				// ステータスバーらしいパネルを探す（下部に配置されているパネル）
				// または特定の名前やクラス名で識別
				String panelName = panel.getName();
				if (panelName != null && (panelName.contains("Status") || panelName.contains("status"))) {
					return panel;
				}
				// 子コンポーネントにJLabelがある場合もステータスバーの可能性がある
				for (Component child : panel.getComponents()) {
					if (child instanceof JLabel) {
						// ステータスバーとして扱う
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
	
	/**
	 * Viridis color palette (approximation).
	 */
	private Color viridisColor(float ratio) {
		// Simplified Viridis approximation
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
	
	/**
	 * Plasma color palette (approximation).
	 */
	private Color plasmaColor(float ratio) {
		// Simplified Plasma approximation
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
	
	/**
	 * Rainbow color palette.
	 */
	private Color rainbowColor(float ratio) {
		float hue = ratio * 0.7f; // 0.7 = blue to red range
		return Color.getHSBColor(hue, 1.0f, 1.0f);
	}

	public void showStations(List<Station> stations) throws Exception {
		showStations(stations, null, null);
	}
	
	public void showStations(List<Station> stations, String colorColumn, double[] colorValues) throws Exception {
		// 既存のステーションレイヤーをクリア（disposeを呼び出してメモリリークを防ぐ）
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
				// Ignore dispose errors
			}
			map.removeLayer(layer);
		}

		SimpleFeatureType type = DataUtilities.createType("Station", "geom:Point,code:String,lat:Double,lon:Double,dep:Double");
		GeometryFactory gf = new GeometryFactory();
		
		// 色付け用の処理
		if (colorColumn != null && colorValues != null && colorValues.length == stations.size()) {
			// 最小値と最大値を決定
			double minValue = Double.MAX_VALUE;
			double maxValue = Double.MIN_VALUE;
			for (double val : colorValues) {
				if (val < minValue) minValue = val;
				if (val > maxValue) maxValue = val;
			}
			
			// 色ごとにFeatureCollectionを分割
			java.util.Map<Color, DefaultFeatureCollection> colorFeatureCollections = new java.util.HashMap<>();
			
			for (int i = 0; i < stations.size(); i++) {
				Station s = stations.get(i);
				double valueToColor = colorValues[i];
				
				// 正規化（0-1の範囲）
				double normalized = (maxValue > minValue) 
					? (valueToColor - minValue) / (maxValue - minValue) 
					: 0.0;
				
				// Apply color palette
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
			
			// 各色に対応するレイヤーを追加
			for (java.util.Map.Entry<Color, DefaultFeatureCollection> entry : colorFeatureCollections.entrySet()) {
				Color color = entry.getKey();
				DefaultFeatureCollection col = entry.getValue();
				Style style = StyleFactory.createColorStyle(color.getRed(), color.getGreen(), color.getBlue());
				FeatureLayer layer = new FeatureLayer(col, style);
				layer.setTitle("StationLayer");
				map.addLayer(layer);
			}
			
			// カラーバーを更新
			if (stations.size() > 0) {
				String label = colorColumn;
				setColorRange(minValue, maxValue, label);
			}
		} else {
			// 色付けなしの通常表示
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
	}
	
	/**
	 * 選択されたポイントを強調表示
	 */
	public void highlightPoint(double lon, double lat) throws Exception {
		highlightPoint(lon, lat, null);
	}
	
	public void highlightPoint(double lon, double lat, String type) {
		clearHighlight(); // 既存のハイライトをクリア

		try {
			SimpleFeatureType highlightType = DataUtilities.createType("Highlight", "geom:Point");
			DefaultFeatureCollection highlightCollection = new DefaultFeatureCollection("HighlightLayer", highlightType);
			GeometryFactory gf = new GeometryFactory();

			SimpleFeature feature = DataUtilities.template(highlightType);
			feature.setDefaultGeometry(gf.createPoint(new Coordinate(lon, lat)));
			highlightCollection.add(feature);

			FeatureLayer highlightLayer = new FeatureLayer(highlightCollection, StyleFactory.selectedPointStyle());
			highlightLayer.setTitle("Selected");
			map.addLayer(highlightLayer);
		} catch (Exception e) {
			System.err.println("ハイライト表示に失敗: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * 強調表示をクリア
	 */
	public void clearHighlight() {
		removeHighlightLayer();
	}
	
	/**
	 * 強調表示レイヤーを削除
	 */
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
				// Ignore dispose errors
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
		// 既存のレイヤーを再描画（エラーハンドリングを追加）
		try {
			SwingUtilities.invokeLater(() -> {
				try {
					frame.getMapPane().repaint();
				} catch (Exception e) {
					// GeoToolsのレンダリングエラーを抑制（NullPointerExceptionなど）
					if (e instanceof NullPointerException && 
						(e.getMessage() == null || e.getMessage().contains("loops"))) {
						// GeoToolsの既知のバグを無視
						return;
					}
					// その他のエラーはログに記録
					java.util.logging.Logger.getLogger(MapView.class.getName())
						.warning("マップの再描画に失敗: " + e.getMessage());
				}
			});
		} catch (Exception e) {
			// エラーを無視（GeoToolsの既知のバグ）
			java.util.logging.Logger.getLogger(MapView.class.getName())
				.warning("マップ更新のスケジューリングに失敗: " + e.getMessage());
		}
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
		if (paletteCombo != null) {
			paletteCombo.setSelectedItem(palette);
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
	
	/**
	 * Shows a file chooser dialog and exports the map as an image.
	 */
	private void exportMapImage() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("地図を画像として出力");
		fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"PNG files (*.png)", "png"));
		fileChooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg"));
		fileChooser.setSelectedFile(new File("map.png"));
		
		int result = fileChooser.showSaveDialog(frame);
		if (result == JFileChooser.APPROVE_OPTION) {
			File outputFile = fileChooser.getSelectedFile();
			try {
				exportMapImageToFile(outputFile);
				JOptionPane.showMessageDialog(frame,
					"地図を画像として出力しました: " + outputFile.getAbsolutePath(),
					"情報", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(frame,
					"画像の出力に失敗しました: " + e.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	/**
	 * Exports the current map view as an image file.
	 * 
	 * @param outputFile the output file (PNG or JPEG)
	 * @throws Exception if export fails
	 */
	public void exportMapImageToFile(File outputFile) throws Exception {
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
		} else if ("jpg".equals(extension) || "jpeg".equals(extension)) {
			javax.imageio.ImageIO.write(image, "JPEG", outputFile);
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
	 * カラーバーを表示するパネル
	 */
	private static class ColorBarPanel extends JPanel {
		private double minValue = 0.0;
		private double maxValue = 100.0;
		private String label = "深度 (km)";
		private ColorPalette palette = ColorPalette.BLUE_TO_RED;
		private JComboBox<ColorPalette> paletteCombo = null;
		private JPanel colorBarDrawingPanel;
		
		public ColorBarPanel(ColorPalette palette) {
			this.palette = palette;
			setPreferredSize(new java.awt.Dimension(450, 100)); // Increased height to prevent clipping
			setMinimumSize(new java.awt.Dimension(350, 100));
			setLayout(new BorderLayout(5, 5));
			setBorder(BorderFactory.createTitledBorder(label));
			
			// Create panel for drawing color bar
			colorBarDrawingPanel = new JPanel() {
				@Override
				protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					
					int width = getWidth() - 20;
					int height = getHeight() - 50; // More space for labels to prevent clipping
					int x = 10;
					int y = 25;
					
					// Draw color bar with current palette
					for (int i = 0; i < width; i++) {
						float ratio = (float) i / width;
						Color color = getColorFromPalette(ratio);
						g2d.setColor(color);
						g2d.drawLine(x + i, y, x + i, y + height);
					}
					
					// Draw labels with better visibility (white background with black border)
					FontMetrics fm = g2d.getFontMetrics();
					String minLabel = String.format("%.1f", minValue);
					String maxLabel = String.format("%.1f", maxValue);
					
					// Draw labels below the color bar with background for visibility
					int labelY = y + height + 18;
					int labelPadding = 3;
					
					// Draw background and border for min label
					int minLabelWidth = fm.stringWidth(minLabel) + labelPadding * 2;
					int minLabelHeight = fm.getHeight() + labelPadding * 2;
					g2d.setColor(Color.WHITE);
					g2d.fillRect(x - labelPadding, labelY - fm.getAscent() - labelPadding, 
						minLabelWidth, minLabelHeight);
					g2d.setColor(Color.BLACK);
					g2d.drawRect(x - labelPadding, labelY - fm.getAscent() - labelPadding, 
						minLabelWidth, minLabelHeight);
					g2d.drawString(minLabel, x, labelY);
					
					// Draw background and border for max label
					int maxLabelWidth = fm.stringWidth(maxLabel) + labelPadding * 2;
					int maxLabelHeight = fm.getHeight() + labelPadding * 2;
					g2d.setColor(Color.WHITE);
					g2d.fillRect(x + width - maxLabelWidth + labelPadding, labelY - fm.getAscent() - labelPadding,
						maxLabelWidth, maxLabelHeight);
					g2d.setColor(Color.BLACK);
					g2d.drawRect(x + width - maxLabelWidth + labelPadding, labelY - fm.getAscent() - labelPadding,
						maxLabelWidth, maxLabelHeight);
					g2d.drawString(maxLabel, x + width - fm.stringWidth(maxLabel), labelY);
				}
				
				private Color getColorFromPalette(float ratio) {
					switch (ColorBarPanel.this.palette) {
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
			};
			colorBarDrawingPanel.setOpaque(false);
			add(colorBarDrawingPanel, BorderLayout.CENTER);
		}
		
		public void addPaletteSelector(JComboBox<ColorPalette> combo) {
			this.paletteCombo = combo;
			// Add palette selector on the left side, next to color bar
			JPanel palettePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
			palettePanel.setOpaque(false);
			palettePanel.setPreferredSize(new java.awt.Dimension(100, 30));
			palettePanel.add(new JLabel("Palette:"));
			palettePanel.add(combo);
			add(palettePanel, BorderLayout.WEST);
		}
		
		public void setRange(double min, double max, String label) {
			this.minValue = min;
			this.maxValue = max;
			this.label = label;
			setBorder(BorderFactory.createTitledBorder(label));
			if (colorBarDrawingPanel != null) {
				colorBarDrawingPanel.repaint();
			}
		}
		
		public void setPalette(ColorPalette palette) {
			this.palette = palette;
			if (colorBarDrawingPanel != null) {
				colorBarDrawingPanel.repaint();
			}
		}
	}
	
}
