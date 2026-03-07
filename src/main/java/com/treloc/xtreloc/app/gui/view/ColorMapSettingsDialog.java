package com.treloc.xtreloc.app.gui.view;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.treloc.xtreloc.app.gui.util.FileChooserHelper;
import com.treloc.xtreloc.app.gui.view.MapView.ColorBarPanel;
import com.treloc.xtreloc.app.gui.view.MapView.ColorPalette;

/**
 * Dialog for configuring color map settings and exporting color maps.
 */
public class ColorMapSettingsDialog extends JDialog {
	private ColorBarPanel colorBarPanel;
	private JComboBox<ColorPalette> paletteCombo;
	private JTextField minValueField;
	private JTextField maxValueField;
	private JSpinner numLabelsSpinner;
	private JCheckBox labelsVisibleCheckBox;
	private JButton backgroundColorButton;
	private JButton foregroundColorButton;
	private Color backgroundColor = Color.BLACK;
	private Color foregroundColor = Color.WHITE;
	
	public ColorMapSettingsDialog(Window parent, ColorBarPanel colorBarPanel) {
		super(parent, "Color Map Settings", ModalityType.APPLICATION_MODAL);
		this.colorBarPanel = colorBarPanel;
		
		setSize(500, 450);
		setLocationRelativeTo(parent);
		setLayout(new BorderLayout(10, 10));
		
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		JPanel palettePanel = createPalettePanel();
		JPanel rangePanel = createRangePanel();
		JPanel labelPanel = createLabelPanel();
		JPanel colorPanel = createColorPanel();
		JPanel buttonPanel = createButtonPanel();
		
		mainPanel.add(palettePanel);
		mainPanel.add(Box.createVerticalStrut(10));
		mainPanel.add(rangePanel);
		mainPanel.add(Box.createVerticalStrut(10));
		mainPanel.add(labelPanel);
		mainPanel.add(Box.createVerticalStrut(10));
		mainPanel.add(colorPanel);
		mainPanel.add(Box.createVerticalGlue());
		mainPanel.add(buttonPanel);
		
		add(mainPanel, BorderLayout.CENTER);
		
		loadCurrentSettings();
	}
	
	private JPanel createPalettePanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		panel.setBorder(new TitledBorder("Color Palette"));
		
		paletteCombo = new JComboBox<>(ColorPalette.values());
		paletteCombo.setSelectedItem(colorBarPanel.getPalette());
		paletteCombo.addActionListener(e -> {
			ColorPalette selected = (ColorPalette) paletteCombo.getSelectedItem();
			colorBarPanel.setPalette(selected);
		});
		
		panel.add(new JLabel("Palette:"));
		panel.add(paletteCombo);
		
		return panel;
	}
	
	private JPanel createRangePanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		panel.setBorder(new TitledBorder("Value Range"));
		
		minValueField = new JTextField(String.format("%.2f", colorBarPanel.getMinValue()), 10);
		maxValueField = new JTextField(String.format("%.2f", colorBarPanel.getMaxValue()), 10);
		
		minValueField.addActionListener(e -> updateRange());
		maxValueField.addActionListener(e -> updateRange());
		
		minValueField.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				updateRange();
			}
		});
		
		maxValueField.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				updateRange();
			}
		});
		
		panel.add(new JLabel("Min:"));
		panel.add(minValueField);
		panel.add(new JLabel("Max:"));
		panel.add(maxValueField);
		
		return panel;
	}
	
	private JPanel createLabelPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		panel.setBorder(new TitledBorder("Labels"));
		
		numLabelsSpinner = new JSpinner(new SpinnerNumberModel(colorBarPanel.getNumLabels(), 2, 20, 1));
		numLabelsSpinner.addChangeListener(e -> {
			int numLabels = (Integer) numLabelsSpinner.getValue();
			colorBarPanel.setNumLabels(numLabels);
		});
		
		labelsVisibleCheckBox = new JCheckBox("Show Labels", colorBarPanel.isLabelsVisible());
		labelsVisibleCheckBox.addActionListener(e -> {
			colorBarPanel.setLabelsVisible(labelsVisibleCheckBox.isSelected());
		});
		
		panel.add(new JLabel("Number of Labels:"));
		panel.add(numLabelsSpinner);
		panel.add(labelsVisibleCheckBox);
		
		return panel;
	}
	
	private JPanel createColorPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		panel.setBorder(new TitledBorder("CPT Export Colors"));
		
		backgroundColorButton = new JButton("Background");
		backgroundColorButton.setBackground(backgroundColor);
		backgroundColorButton.setForeground(Color.WHITE);
		backgroundColorButton.addActionListener(e -> {
			Color newColor = JColorChooser.showDialog(this, "Choose Background Color", backgroundColor);
			if (newColor != null) {
				backgroundColor = newColor;
				backgroundColorButton.setBackground(backgroundColor);
				backgroundColorButton.setForeground(
					(backgroundColor.getRed() + backgroundColor.getGreen() + backgroundColor.getBlue()) / 3 < 128 
						? Color.WHITE : Color.BLACK);
			}
		});
		
		foregroundColorButton = new JButton("Foreground");
		foregroundColorButton.setBackground(foregroundColor);
		foregroundColorButton.setForeground(Color.BLACK);
		foregroundColorButton.addActionListener(e -> {
			Color newColor = JColorChooser.showDialog(this, "Choose Foreground Color", foregroundColor);
			if (newColor != null) {
				foregroundColor = newColor;
				foregroundColorButton.setBackground(foregroundColor);
				foregroundColorButton.setForeground(
					(foregroundColor.getRed() + foregroundColor.getGreen() + foregroundColor.getBlue()) / 3 < 128 
						? Color.WHITE : Color.BLACK);
			}
		});
		
		panel.add(new JLabel("Background (B):"));
		panel.add(backgroundColorButton);
		panel.add(new JLabel("Foreground (F):"));
		panel.add(foregroundColorButton);
		
		return panel;
	}
	
	private JPanel createButtonPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
		
		JButton exportCPTButton = new JButton("Export CPT");
		exportCPTButton.addActionListener(e -> exportCPT());
		
		JButton exportJSONButton = new JButton("Export JSON");
		exportJSONButton.addActionListener(e -> exportJSON());
		
		JButton exportCSVButton = new JButton("Export CSV");
		exportCSVButton.addActionListener(e -> exportCSV());
		
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dispose());
		
		panel.add(exportCPTButton);
		panel.add(exportJSONButton);
		panel.add(exportCSVButton);
		panel.add(closeButton);
		
		return panel;
	}
	
	private void loadCurrentSettings() {
		paletteCombo.setSelectedItem(colorBarPanel.getPalette());
		minValueField.setText(String.format("%.2f", colorBarPanel.getMinValue()));
		maxValueField.setText(String.format("%.2f", colorBarPanel.getMaxValue()));
		numLabelsSpinner.setValue(colorBarPanel.getNumLabels());
		labelsVisibleCheckBox.setSelected(colorBarPanel.isLabelsVisible());
	}
	
	private void updateRange() {
		try {
			double newMin = Double.parseDouble(minValueField.getText().trim());
			double newMax = Double.parseDouble(maxValueField.getText().trim());
			
			if (newMin < newMax) {
				colorBarPanel.setRange(newMin, newMax, colorBarPanel.getLabel());
			} else {
				JOptionPane.showMessageDialog(this, "Min value must be less than Max value", 
					"Invalid Range", JOptionPane.ERROR_MESSAGE);
				minValueField.setText(String.format("%.2f", colorBarPanel.getMinValue()));
				maxValueField.setText(String.format("%.2f", colorBarPanel.getMaxValue()));
			}
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(this, "Invalid number format", 
				"Error", JOptionPane.ERROR_MESSAGE);
			minValueField.setText(String.format("%.2f", colorBarPanel.getMinValue()));
			maxValueField.setText(String.format("%.2f", colorBarPanel.getMaxValue()));
		}
	}
	
	private void exportCPT() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export CPT File");
		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".cpt");
			}
			
			@Override
			public String getDescription() {
				return "CPT Files (*.cpt)";
			}
		});
		FileChooserHelper.setDefaultDirectory(fileChooser);
		
		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			if (!file.getName().toLowerCase().endsWith(".cpt")) {
				file = new File(file.getParent(), file.getName() + ".cpt");
			}
			
			try (FileWriter writer = new FileWriter(file)) {
				writeCPTFile(writer);
				JOptionPane.showMessageDialog(this, "CPT file exported successfully: " + file.getName(),
					"Export Successful", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, "Failed to export CPT file: " + e.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	private void writeCPTFile(FileWriter writer) throws IOException {
		Color[] colors = colorBarPanel.getColorArray();
		if (colors == null || colors.length == 0) {
			return;
		}
		
		double minValue = colorBarPanel.getMinValue();
		double maxValue = colorBarPanel.getMaxValue();
		int numSteps = colors.length;
		
		for (int i = 0; i < numSteps - 1; i++) {
			double z1 = minValue + (maxValue - minValue) * i / (numSteps - 1);
			double z2 = minValue + (maxValue - minValue) * (i + 1) / (numSteps - 1);
			
			Color c1 = colors[i];
			Color c2 = colors[i + 1];
			
			writer.write(String.format("%.6f\t%d\t%d\t%d\t%.6f\t%d\t%d\t%d\n",
				z1, c1.getRed(), c1.getGreen(), c1.getBlue(),
				z2, c2.getRed(), c2.getGreen(), c2.getBlue()));
		}
		
		writer.write(String.format("B\t%d\t%d\t%d\n",
			backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue()));
		writer.write(String.format("F\t%d\t%d\t%d\n",
			foregroundColor.getRed(), foregroundColor.getGreen(), foregroundColor.getBlue()));
	}
	
	private void exportJSON() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export JSON File");
		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".json");
			}
			
			@Override
			public String getDescription() {
				return "JSON Files (*.json)";
			}
		});
		FileChooserHelper.setDefaultDirectory(fileChooser);
		
		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			if (!file.getName().toLowerCase().endsWith(".json")) {
				file = new File(file.getParent(), file.getName() + ".json");
			}
			
			try (FileWriter writer = new FileWriter(file)) {
				writeJSONFile(writer);
				JOptionPane.showMessageDialog(this, "JSON file exported successfully: " + file.getName(),
					"Export Successful", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, "Failed to export JSON file: " + e.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	private void writeJSONFile(FileWriter writer) throws IOException {
		Color[] colors = colorBarPanel.getColorArray();
		if (colors == null || colors.length == 0) {
			return;
		}
		
		writer.write("{\n");
		writer.write("  \"palette\": \"" + colorBarPanel.getPalette().name() + "\",\n");
		writer.write("  \"minValue\": " + colorBarPanel.getMinValue() + ",\n");
		writer.write("  \"maxValue\": " + colorBarPanel.getMaxValue() + ",\n");
		writer.write("  \"numLabels\": " + colorBarPanel.getNumLabels() + ",\n");
		writer.write("  \"labelsVisible\": " + colorBarPanel.isLabelsVisible() + ",\n");
		writer.write("  \"backgroundColor\": [" + 
			backgroundColor.getRed() + ", " + backgroundColor.getGreen() + ", " + backgroundColor.getBlue() + "],\n");
		writer.write("  \"foregroundColor\": [" + 
			foregroundColor.getRed() + ", " + foregroundColor.getGreen() + ", " + foregroundColor.getBlue() + "],\n");
		writer.write("  \"colors\": [\n");
		
		for (int i = 0; i < colors.length; i++) {
			Color c = colors[i];
			writer.write("    [" + c.getRed() + ", " + c.getGreen() + ", " + c.getBlue() + "]");
			if (i < colors.length - 1) {
				writer.write(",");
			}
			writer.write("\n");
		}
		
		writer.write("  ]\n");
		writer.write("}\n");
	}
	
	private void exportCSV() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Export CSV File");
		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isDirectory() || f.getName().toLowerCase().endsWith(".csv");
			}
			
			@Override
			public String getDescription() {
				return "CSV Files (*.csv)";
			}
		});
		FileChooserHelper.setDefaultDirectory(fileChooser);
		
		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			if (!file.getName().toLowerCase().endsWith(".csv")) {
				file = new File(file.getParent(), file.getName() + ".csv");
			}
			
			try (FileWriter writer = new FileWriter(file)) {
				writeCSVFile(writer);
				JOptionPane.showMessageDialog(this, "CSV file exported successfully: " + file.getName(),
					"Export Successful", JOptionPane.INFORMATION_MESSAGE);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, "Failed to export CSV file: " + e.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
	
	private void writeCSVFile(FileWriter writer) throws IOException {
		Color[] colors = colorBarPanel.getColorArray();
		if (colors == null || colors.length == 0) {
			return;
		}
		
		double minValue = colorBarPanel.getMinValue();
		double maxValue = colorBarPanel.getMaxValue();
		
		writer.write("Value,R,G,B\n");
		
		for (int i = 0; i < colors.length; i++) {
			double value = minValue + (maxValue - minValue) * i / (colors.length - 1);
			Color c = colors[i];
			writer.write(String.format("%.6f,%d,%d,%d\n", value, c.getRed(), c.getGreen(), c.getBlue()));
		}
	}
}

