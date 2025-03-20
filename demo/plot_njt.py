#! /usr/bin/env python3
# -*- coding: utf-8 -*-

import netCDF4
import numpy as np
import pandas as pd
from mpl_toolkits.basemap import Basemap
import matplotlib.pyplot as plt

plt.style.use('ggplot')
plt.rcParams['font.family'] = 'serif'

df = pd.read_csv('catalog.list',
	header=None,
	names=['time','lat','lon','dep','elat','elon','edep','res','file','method','cid'],
	parse_dates=['time'],
	delimiter=' ')

stn = pd.read_csv('station.tbl',
				  delim_whitespace=True,
				  names=['name', 'lat', 'lon', 'elev', 'pc', 'sc', 'ed1', 'ed2', 'ed3'],
				  comment='#')

xmin = 141.5
xmax = 144.5
ymin = 37.5
ymax = 40.5

fig, ax = plt.subplots(figsize=(9,10))

m = Basemap(projection='merc',
			llcrnrlat=ymin,
			urcrnrlat=ymax,
			llcrnrlon=xmin,
			urcrnrlon=xmax,
			resolution='h',
			ax=ax)

# seafloor = netCDF4.Dataset('path/to/grd/file', 'r')
# lons = seafloor.variables['x'][:]
# lats = seafloor.variables['y'][:]
# depth = seafloor.variables['z'][:]
# lon, lat = np.meshgrid(lons, lats)
# x, y = m(lon, lat)
# m.contourf(x, y, depth, cmap='Blues_r', alpha=0.7)

m.drawmapboundary()
m.drawcoastlines()
m.drawcountries()
# m.drawrivers()
m.fillcontinents()
m.drawparallels(np.arange(ymin, ymax, 0.5), labels=[1, 0, 0, 0])
m.drawmeridians(np.arange(xmin, xmax, 0.5), labels=[0, 0, 0, 1])
m.drawmapscale(141.8, 40.15, 143, 39.5, 50, units='km', fontsize=14)

# Hypocenter
x, y = m(df['lon'].values, df['lat'].values)
cb = ax.scatter(x, y, s=4, marker='o', c=df['cid'].values, label='Hypo',cmap='gist_ncar')
plt.colorbar(cb,shrink=0.8,label='CID')

# Station
x, y = m(stn['lon'].values, stn['lat'].values)
ax.scatter(x, y, s=100, marker='+', color='k')

ax.legend(loc='lower left', frameon=False)
fig.tight_layout()
plt.savefig('map.pdf')
