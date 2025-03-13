
#filename = "catalog_ground_truth.list"

# Set filenames
new_extension = ".pdf"
dot_position = strstrt(filename, ".")
if (dot_position > 0) {
    base_name = substr(filename, 1, dot_position - 1)
} else {
    base_name = filename
}
set term pdfcairo font 'Times,12' size 5,5
set out base_name . new_extension
set title filename

# Graph settings
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
set ylabel 'Latitude' font ',15' offset 0.5,0 tc rgb 'dark-gray'
#set size ratio -1

# MATLAB jet color pallete (https://github.com/Gnuplotting/gnuplot-palettes)
set style line 11 lt 1 lc rgb '#0000ff' # blue
set style line 12 lt 1 lc rgb '#007f00' # green
set style line 13 lt 1 lc rgb '#ff0000' # red
set style line 14 lt 1 lc rgb '#00bfbf' # cyan
set style line 15 lt 1 lc rgb '#bf00bf' # pink
set style line 16 lt 1 lc rgb '#bfbf00' # yellow
set style line 17 lt 1 lc rgb '#3f3f3f' # black
set palette defined (0  0.0 0.0 0.5, \
                     1  0.0 0.0 1.0, \
                     2  0.0 0.5 1.0, \
                     3  0.0 1.0 1.0, \
                     4  0.5 1.0 0.5, \
                     5  1.0 1.0 0.0, \
                     6  1.0 0.5 0.0, \
                     7  1.0 0.0 0.0, \
                     8  0.5 0.0 0.0 )

# Colorbar
set cblabel 'Depth' font ',15' tc rgb 'dark-gray'
set cbrange [10:20]
set cbtics 5

# Plot
plot 'world_10m.txt' u 1:2 w filledcurves ls 2 lc rgb "gold",\
     '' u 1:2 w l ls 2 lc rgb "dark-gray",\
     filename u 3:2:($6/111):($5/111):4 with xyerrorbars pt 4 lw 2 lc palette,\
     'station.tbl' u 3:2 with points pt 1 lc rgb 'black'
