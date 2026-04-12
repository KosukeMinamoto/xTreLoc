#!/bin/bash
# ===============================
# GMT script for plotting catalog and stations on a map.
#
# Usage:
#   ./map_njt.sh [catalog_file] [station_file] [output_file]
#
# Examples:
#   ./map_njt.sh ./catalog_ground_truth.csv ./station.tbl catalog_map.pdf
#   ./map_njt.sh ./dat-trd/catalog_trd.csv ./station.tbl catalog_trd.pdf
#
# Requirements:
#   - GMT (Generic Mapping Tools) version 6 or later
#   - awk
# ===============================

# ===============================
# Input files
# ===============================
FILENAME_CSV="${1:-./catalog_ground_truth.csv}"
FILENAME_STATION="${2:-./station.tbl}"
FILENAME_PDF="${3:-catalog_map.pdf}"
FILENAME_TMP="catalog.tmp"

# ===============================
# Map settings
# ===============================
LON_MIN=141.5
LON_MAX=144.0
LAT_MIN=38.0
LAT_MAX=40.5
DEPTH_MIN=10
DEPTH_MAX=20

# ===============================
# GMT settings
# ===============================
GMT_PROJ="M5i"  # Mercator projection, 5 inches width
GMT_REGION="${LON_MIN}/${LON_MAX}/${LAT_MIN}/${LAT_MAX}"
GMT_PS="${FILENAME_PDF%.pdf}.ps"

# ===============================
# Make tmp file (space separated: lon lat depth xerr yerr)
# ===============================
echo "Processing CSV file: ${FILENAME_CSV}"
awk -F, 'NR>1 {print $3, $2, $4, $5, $6}' "${FILENAME_CSV}" > "${FILENAME_TMP}"

# ===============================
# Create custom colormap (CPT file)
# ===============================
# Colors matching Gnuplot palette
# Calculate depth intervals (8 intervals for 9 colors)
DEPTH_RANGE=$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MAX} - ${DEPTH_MIN}}")
DEPTH_STEP=$(awk "BEGIN {printf \"%.3f\", ${DEPTH_RANGE} / 8.0}")

# Create CPT file directly with proper intervals
# Format: z1 r1 g1 b1 z2 r2 g2 b2
cat > depth.cpt << EOF
# COLOR_MODEL = RGB
# Depth colormap matching Gnuplot palette
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN}}")	0	0	128	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + ${DEPTH_STEP}}")	0	0	255
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + ${DEPTH_STEP}}")	0	0	255	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 2*${DEPTH_STEP}}")	0	128	255
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 2*${DEPTH_STEP}}")	0	128	255	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 3*${DEPTH_STEP}}")	0	255	255
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 3*${DEPTH_STEP}}")	0	255	255	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 4*${DEPTH_STEP}}")	128	255	128
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 4*${DEPTH_STEP}}")	128	255	128	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 5*${DEPTH_STEP}}")	255	255	0
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 5*${DEPTH_STEP}}")	255	255	0	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 6*${DEPTH_STEP}}")	255	128	0
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 6*${DEPTH_STEP}}")	255	128	0	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 7*${DEPTH_STEP}}")	255	0	0
$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MIN} + 7*${DEPTH_STEP}}")	255	0	0	$(awk "BEGIN {printf \"%.3f\", ${DEPTH_MAX}}")	128	0	0
B	0	0	0
F	255	255	255
N	128	128	128
EOF

# Alternative: use built-in colormap (uncomment to use instead)
# gmt makecpt -Cjet -T${DEPTH_MIN}/${DEPTH_MAX}/0.5 -I > depth.cpt

# ===============================
# Start GMT session
# ===============================
gmt begin "${FILENAME_PDF%.pdf}" pdf

# ===============================
# Set region and projection
# ===============================
gmt set MAP_FRAME_TYPE plain
gmt set MAP_GRID_PEN_PRIMARY 0.5p,darkgray,-
gmt set MAP_TICK_PEN_PRIMARY 0.5p,darkgray
gmt set FONT_ANNOT_PRIMARY 12p,Times-Roman,darkgray
gmt set FONT_LABEL 15p,Times-Roman,darkgray
gmt set FORMAT_GEO_MAP ddd.xx

gmt basemap -R${GMT_REGION} -J${GMT_PROJ} \
    -Bxa0.5f0.1+l"Longitude" -Bya0.5f0.1+l"Latitude" \
    -BWSne+t"$(basename ${FILENAME_CSV})" \
    -Xc -Yc

# ===============================
# Plot coastlines and land
# ===============================
gmt coast -R${GMT_REGION} -J${GMT_PROJ} \
    -Ggold -W0.5p,darkgray -Df

# ===============================
# Plot catalog data with error bars
# ===============================
# Format: lon lat depth xerr yerr
# Convert error from km to degrees (1 degree ≈ 111 km)
# Create error bar file: lon lat xerr yerr (4 columns)
awk '{print $1, $2, $4/111.0, $5/111.0}' "${FILENAME_TMP}" > catalog_errors.tmp

# Plot error bars (xy error bars)
# Format for -Sx: lon lat xerr yerr (4 columns)
gmt plot catalog_errors.tmp -R${GMT_REGION} -J${GMT_PROJ} \
    -Sx -W0.3p,gray@50

# Plot catalog points colored by depth
# Format: lon lat depth
gmt plot "${FILENAME_TMP}" -R${GMT_REGION} -J${GMT_PROJ} \
    -Sc0.15c -Cdepth.cpt -i0,1,2 \
    -W0.3p,black

# ===============================
# Plot stations
# ===============================
# Station file format: name lat lon ...
# Extract lon (col 3) and lat (col 2)
awk '{print $3, $2}' "${FILENAME_STATION}" > stations.tmp

gmt plot stations.tmp -R${GMT_REGION} -J${GMT_PROJ} \
    -S+0.3c -W1.5p,black

# ===============================
# Add colorbar
# ===============================
gmt colorbar -Cdepth.cpt -Dx0.5i/0.5i+w2.5i/0.2i+h \
    -Bxa5f1+l"Depth [km]" \
    -G${DEPTH_MIN}/${DEPTH_MAX} \
    --FONT_ANNOT_PRIMARY=12p,Times-Roman,darkgray \
    --FONT_LABEL=12p,Times-Roman,darkgray

# ===============================
# Add axis labels (handled by basemap -B option)
# ===============================
# Labels are automatically added by basemap -B option

# ===============================
# End GMT session
# ===============================
gmt end show

# ===============================
# Clean up temporary files
# ===============================
rm -f "${FILENAME_TMP}" catalog_errors.tmp stations.tmp depth.cpt

echo "Map saved to ${FILENAME_PDF}"

