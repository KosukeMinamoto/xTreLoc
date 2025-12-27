# ===============================
# Input files
# ===============================
filename_csv = "./dat/catalog.csv"
filename_tmp = "./dat/catalog.tmp"
filename_pdf = "catalog_perturbed.pdf"
filename_station = "./station.tbl"
filename_world = "./world_10m.txt"

# ===============================
# Make tmp file (space separated)
# ===============================
system(sprintf("awk -F, 'NR>1 {print $2, $3, $4, $5, $6}' %s > %s", filename_csv, filename_tmp))

# ===============================
# Output PDF
# ===============================
set term pdfcairo font 'Times,12' size 5,5
set output filename_pdf
set title filename_csv

# ===============================
# Graph settings
# ===============================
unset key
set grid lw 1 lc rgb 'dark-gray'
set tics tc rgb 'dark-gray'
set xtics geographic
set ytics geographic
set format x '%.2d°%E'
set format y '%.2d°%N'
set xr [141.5:144]
set yr [38:40.5]
set xlabel 'Longitude' font ',15' offset 0,0.5 tc rgb 'dark-gray'
set ylabel 'Latitude'  font ',15' offset 0.5,0 tc rgb 'dark-gray'

# ===============================
# Palette & Colorbar
# ===============================
set palette defined (0  0.0 0.0 0.5, \
                     1  0.0 0.0 1.0, \
                     2  0.0 0.5 1.0, \
                     3  0.0 1.0 1.0, \
                     4  0.5 1.0 0.5, \
                     5  1.0 1.0 0.0, \
                     6  1.0 0.5 0.0, \
                     7  1.0 0.0 0.0, \
                     8  0.5 0.0 0.0 )

set cblabel 'Depth'
set cbrange [10:20]
set cbtics 5

# ===============================
# Plot
# ===============================
set datafile separator whitespace

plot \
  filename_world u 1:2 w filledcurves lc rgb "gold", \
  '' u 1:2 w l lc rgb "dark-gray", \
  \
  filename_tmp u 2:1:($5/111):($4/111):3 \
    w xyerrorbars pt 4 lw 2 lc palette, \
  \
  filename_station u 3:2 w p pt 1 lc rgb 'black'

# ===============================
# Remove tmp file
# ===============================
system(sprintf("rm -f %s", filename_tmp))
