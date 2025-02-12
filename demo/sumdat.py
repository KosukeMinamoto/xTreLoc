#! /usr/bin/env python3
# -- coding: utf-8 --

import argparse
from datetime import datetime
from glob import glob
from tqdm import tqdm
import os
import pandas as pd
import warnings
import numpy as np

'''
Summarize dat files in the directory and write to catalog.list

Usage:
	python3 sumdat.py --dat_dir <dat_dir> --map_type <map_type>

Options:
	--dat_dir: Directory of dat files
	--map_type: Type of map to plot
		gnuplot:	gnuplot
		python: 	python
		matlab: 	matlab
		gmt ver6:	gmt6
'''

def get_args():
	parser = argparse.ArgumentParser()
	parser.add_argument(
		"--dat_dir", "-d",
		type=str,
		required=True,
		help="Directory of dat files")
	parser.add_argument(
		"--map_type", "-m",
		type=str,
		required=False,
		help="Type of map to plot",
		default="gnuplot",
		choices=["gnuplot", "python", "matlab", "gmt6"])
	return parser.parse_args()

def sumdat(args: argparse.Namespace) -> pd.DataFrame:
	if os.path.exists("catalog.list"):
		print("Do you want to overwrite catalog.list? (y/n)")
		ans = input()
		if ans.lower() != "y":
			warnings.warn("catalog.list already exists. Exiting...")
			exit(0)
	dat_files = glob(os.path.join(args.dat_dir, "*"))
	catalog = []
	for dat_file in tqdm(dat_files):
		tqdm.write(dat_file)
		df = pd.read_csv(dat_file, sep="\s+", header=None)
		date = datetime.strptime(dat_file.split("/")[-1], "%y%m%d.%H%M%S")
		catalog.append([
			date,
			df.iat[0, 0],
			df.iat[0, 1],
			df.iat[0, 2],
			df.iat[1, 0],
			df.iat[1, 1],
			df.iat[1, 2],
			df.iat[1, 3],
			df.iat[0, 3],
			dat_file,
		])
		# print(catalog)
	df = pd.DataFrame(catalog, columns=["time", "lat", "lon", "dep", "elat", "elon", "edep", "res", "type", "file"])
	df.sort_values(by=["time"], inplace=True)
	df.to_csv("catalog.list", index=False, sep=" ", date_format="%Y-%m-%dT%H:%M:%S")
	return df

def gnuplot_map(lat_min: float, lat_max: float, lon_min: float, lon_max: float):
	f = open("view.plt", "w")
	f.write("set term pdfcairo font 'Times,12' size 5,5\n")
	f.write("set out 'map.pdf'\n")
	f.write("set title 'catalog.list'\n")
	f.write("unset key\n")
	f.write("set grid lw 1 lc rgb 'dark-gray'\n")
	f.write("set tics tc rgb 'dark-gray'\n")
	f.write("set xtics geographic\n")
	f.write("set ytics geographic\n")
	f.write("set format x '%.2d°%E'\n")
	f.write("set format y '%.2d°%N'\n")
	f.write("set xr [" + str(lon_min) + ":" + str(lon_max) + "]\n")
	f.write("set yr [" + str(lat_min) + ":" + str(lat_max) + "]\n")
	f.write("set xlabel 'Longitude' font ',12' off 0,0.5 tc rgb 'dark-gray'\n")
	f.write("set ylabel 'Latitude' font ',12'  tc rgb 'dark-gray'\n")
	f.write("set size ratio -1\n")
	# f.write("set palette defined (0 'blue', 1 'cyan', 2 'green', 3 'yellow', 4 'red')\n")
	f.write("set palette defined ( 0 '#0fffee',1 '#0090ff', 2 '#000fff',3 '#000090',4 '#ffffff',5 '#7f0000', 6 '#ee0000', 7 '#ff7000', 8 '#ffee00')\n")
	f.write("set cblabel 'Depth' font ',10' tc rgb 'dark-gray'\n")
	if os.path.exists("world_10m.txt"):
		f.write("plot 'world_10m.txt' w filledc lc rgb 'gold',\\\n")
	else:
		f.write("plot 'world.dat' w filledc lc rgb 'gold',\\\n")
		print("You can download world_10m.txt from 'https://gnuplotting.org/plotting-the-world-revisited/index.html'")
	f.write("     '' with l ls 2,\\\n")
	f.write("     'catalog.list' using 3:2:($6/111):($5/111):4 with xyerrorbars pt 4 lw 2 lc palette,\\\n")
	f.write("     'station.tbl' using 3:2 with points pt 1 lc rgb 'black'\n")
	f.write("unset multiplot\n")
	f.close()
	os.system("gnuplot view.plt")

def python_map(lat_min: float, lat_max: float, lon_min: float, lon_max: float):
	from mpl_toolkits.basemap import Basemap
	import matplotlib.pyplot as plt
	plt.style.use("ggplot")

	df = pd.read_csv("catalog.list", sep="\s+", parse_dates=["time"])
	fig, ax = plt.subplots(figsize=(6, 6))
	m = Basemap(ax=ax,
				projection='merc',
				llcrnrlon=lon_min,
				llcrnrlat=lat_min,
				urcrnrlon=lon_max,
				urcrnrlat=lat_max,
				resolution='i')
	m.drawcoastlines()
	m.drawcountries()
	m.drawmapboundary(fill_color='white')
	m.drawmeridians(np.arange(lon_min, lon_max, 0.5), labels=[0,0,0,1])
	m.drawparallels(np.arange(lat_min, lat_max, 0.5), labels=[1,0,0,0])
	x, y = m(df['lon'].values, df['lat'].values)
	m.scatter(x, y, c=df["dep"], cmap="jet", s=10)
	fig.tight_layout()
	plt.show()

def matlab_map(lat_min: float, lat_max: float, lon_min: float, lon_max: float):
	f = open('view.m', 'w')
	f.write('clear; clc; close all;\n')
	f.write('hyp = readtable("catalog.list", "Filetype", "text", "Delimiter", " ");\n')
	f.write('stn = readtable("station.tbl", "Filetype", "text", "Delimiter", " ");\n')
	f.write('latLim = [' + str(lat_min) + ', ' + str(lat_max) + '];\n')
	f.write('lonLim = [' + str(lon_min) + ', ' + str(lon_max) + '];\n')
	f.write('figure\n')
	f.write('grid on\n')
	f.write('ax=worldmap(latLim, lonLim);\n')
	f.write('setm(ax, "FFaceColor", [0.9 0.9 0.9]);\n')
	f.write('load coastlines\n')
	f.write('geoshow(coastlat, coastlon, "DisplayType", "line", "Color", "k", "LineWidth", 1.5);\n')
	f.write('scatterm(stn.Var2, stn.Var3, "x", "k");\n')
	f.write('scatterm(hyp.Var2, hyp.Var3, 10, hyp.Var4);\n')
	f.write('cb=colorbar();\n')
	f.write('ylabel(cb,"Depth [km]");\n')
	f.write('colormap jet\n')
	f.close()
	# os.system("matlab -nodesktop -r map")

def gmt6_map(lat_min: float, lat_max: float, lon_min: float, lon_max: float):
	f = open("view.gmt", "w")
	f.write("#!/bin/bash\n")
	f.write("range=" + str(lon_min) + "/" + str(lon_max) + "/" + str(lat_min) + "/" + str(lat_max) + "\n")
	f.write("cat=$1\n")
	f.write("stn=station.tbl\n")
	f.write("gmt begin map pdf\n")
	f.write("	gmt basemap -R$range -Jm6 -B+t$file\n")
	f.write("	gmt coast -Df -W0.25 -Ggold -Bafg -BneWS\n")
	f.write("	awk '{print $3,$2,$4}' $cat | gmt plot -Sc0.2 -Gred -W0.5p,black -l$cat\n")
	f.write("	awk '{print $3,$2}' $stn | gmt plot -S+0.3 -W2,black -l$stn\n")
	f.write("	awk '{print $3,$2,$1}' $stn | gmt text -F2 -D0/0.5 -W0.01,black\n")
	f.write("gmt end #show\n")
	f.close()
	os.system("bash view.gmt catalog.list")

def main(lat_min: float, lat_max: float, lon_min: float, lon_max: float):
	global args
	args = get_args()

	try:
		_ = sumdat(args)
	except FileNotFoundError:
		warnings.warn("No such file or directory. Exiting...")
		exit(1)

	if args.map_type == "gnuplot":
		gnuplot_map(lat_min, lat_max, lon_min, lon_max)
	elif args.map_type == "python":
		python_map(lat_min, lat_max, lon_min, lon_max)
	elif args.map_type == "matlab":
		matlab_map(lat_min, lat_max, lon_min, lon_max)
	elif args.map_type == "gmt6":
		gmt6_map(lat_min, lat_max, lon_min, lon_max)
	else:
		warnings.warn("Invalid map type. Please choose from gnuplot, python, matlab, or gmt6.")
		exit(1)

if __name__ == "__main__":
	lat_min = 38
	lat_max = 40.5
	lon_min = 141.5
	lon_max = 144
	main(lat_min, lat_max, lon_min, lon_max)
