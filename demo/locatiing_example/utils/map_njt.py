#!/usr/bin/env python3
"""
Python script for plotting catalog and stations on a map.
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import cartopy.crs as ccrs
import cartopy.feature as cfeature
from matplotlib.colors import ListedColormap
import argparse
import os

plt.style.use('bmh')

# ===============================
# Input files
# ===============================
def parse_args():
    parser = argparse.ArgumentParser(description='Plot catalog and stations on a map')
    parser.add_argument('--catalog', type=str, default='./catalog_ground_truth.csv',
                        help='Path to catalog CSV file (default: ./catalog_ground_truth.csv)')
    parser.add_argument('--station', type=str, default='./station.tbl',
                        help='Path to station table file (default: ./station.tbl)')
    parser.add_argument('--output', type=str, default='catalog_map.pdf',
                        help='Output PDF filename (default: catalog_map.pdf)')
    parser.add_argument('--lat-range', type=float, nargs=2, default=[38.0, 40.5],
                        help='Latitude range (default: 38.0 40.5)')
    parser.add_argument('--lon-range', type=float, nargs=2, default=[141.5, 144.0],
                        help='Longitude range (default: 141.5 144.0)')
    parser.add_argument('--depth-range', type=float, nargs=2, default=[10, 20],
                        help='Depth range for colorbar (default: 10 20)')
    return parser.parse_args()

# ===============================
# Custom colormap (similar to Gnuplot palette)
# ===============================
def create_custom_colormap():
    """Create custom colormap matching Gnuplot palette"""
    colors = [
        [0.0, 0.0, 0.5],  # dark blue
        [0.0, 0.0, 1.0],  # blue
        [0.0, 0.5, 1.0],  # light blue
        [0.0, 1.0, 1.0],  # cyan
        [0.5, 1.0, 0.5],  # light green
        [1.0, 1.0, 0.0],  # yellow
        [1.0, 0.5, 0.0],  # orange
        [1.0, 0.0, 0.0],  # red
        [0.5, 0.0, 0.0],  # dark red
    ]
    n_bins = 256
    cmap = ListedColormap(colors)
    return cmap

# ===============================
# Read data
# ===============================
def read_catalog(filename):
    """Read catalog CSV file"""
    df = pd.read_csv(filename)
    return df

def read_station(filename):
    """Read station table file (space-separated)"""
    # Station file format: ST01 39.00 142.20 -1000 0.40 0.68
    # Columns: name, latitude, longitude, elevation, ...
    df = pd.read_csv(filename, sep=r'\s+', header=None,
                     names=['name', 'latitude', 'longitude', 'elevation', 'col5', 'col6'])
    return df

# ===============================
# Main plotting function
# ===============================
def plot_map(catalog_file, station_file, output_file, lat_range, lon_range, depth_range):
    """Create map with catalog and station data"""
    
    # Read data
    catalog = read_catalog(catalog_file)
    station = read_station(station_file)
    
    # Create figure with cartopy projection
    fig = plt.figure(figsize=(8, 8))
    ax = plt.axes(projection=ccrs.PlateCarree())
    
    # Set map extent
    ax.set_extent([lon_range[0], lon_range[1], lat_range[0], lat_range[1]], 
                   crs=ccrs.PlateCarree())
    
    # Add map features
    ax.add_feature(cfeature.LAND, color='gold', alpha=0.5)
    ax.add_feature(cfeature.COASTLINE, linewidth=1, color='darkgray')
    ax.add_feature(cfeature.BORDERS, linewidth=0.5, color='darkgray')
    ax.add_feature(cfeature.OCEAN, color='lightblue', alpha=0.5)
    
    # Add gridlines
    gl = ax.gridlines(draw_labels=True, linewidth=1, color='darkgray', 
                      alpha=0.5, linestyle='--')
    gl.top_labels = False
    gl.right_labels = False
    gl.xlabel_style = {'color': 'darkgray', 'fontsize': 12}
    gl.ylabel_style = {'color': 'darkgray', 'fontsize': 12}
    
    # Format labels
    gl.xformatter = plt.FuncFormatter(lambda x, p: f'{x:.2f}°E')
    gl.yformatter = plt.FuncFormatter(lambda x, p: f'{x:.2f}°N')
    
    # Plot catalog data with depth coloring
    if len(catalog) > 0:
        # Create scatter plot colored by depth
        scatter = ax.scatter(catalog['longitude'], catalog['latitude'],
                            c=catalog['depth'], s=50, cmap=create_custom_colormap(),
                            vmin=depth_range[0], vmax=depth_range[1],
                            edgecolors='black', linewidths=0.5,
                            transform=ccrs.PlateCarree(), zorder=5)
        
        # Add error bars if available
        if 'xerr' in catalog.columns and 'yerr' in catalog.columns:
            # Convert error from degrees to approximate km (1 degree ≈ 111 km)
            xerr_deg = catalog['xerr'] / 111.0
            yerr_deg = catalog['yerr'] / 111.0
            
            # Plot error bars (only for a subset to avoid clutter)
            # You can adjust this to show all or sample
            sample_size = min(100, len(catalog))
            indices = np.random.choice(len(catalog), sample_size, replace=False)
            
            for idx in indices:
                lon = catalog.iloc[idx]['longitude']
                lat = catalog.iloc[idx]['latitude']
                xe = xerr_deg.iloc[idx]
                ye = yerr_deg.iloc[idx]
                ax.errorbar(lon, lat, xerr=xe, yerr=ye, 
                           color='gray', alpha=0.3, linewidth=0.5,
                           transform=ccrs.PlateCarree(), zorder=4)
        
        # Add colorbar
        cbar = plt.colorbar(scatter, ax=ax, orientation='vertical', 
                           pad=0.05, shrink=0.8)
        cbar.set_label('Depth [km]', fontsize=12)
        cbar.set_ticks(np.arange(depth_range[0], depth_range[1] + 1, 5))
    
    # Plot stations
    if len(station) > 0:
        ax.scatter(station['longitude'], station['latitude'],
                  s=100, marker='+', color='black', linewidths=2,
                  transform=ccrs.PlateCarree(), zorder=6, label='Stations')
    
    # Set labels
    ax.set_xlabel('Longitude', fontsize=15, color='darkgray')
    ax.set_ylabel('Latitude', fontsize=15, color='darkgray')
    
    # Set title
    ax.set_title(os.path.basename(catalog_file), fontsize=12, pad=10)
    
    # Save figure
    plt.tight_layout()
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Map saved to {output_file}")
    
    return fig, ax

# ===============================
# Main
# ===============================
if __name__ == '__main__':
    args = parse_args()
    
    # Check if files exist
    if not os.path.exists(args.catalog):
        print(f"Error: Catalog file not found: {args.catalog}")
        exit(1)
    if not os.path.exists(args.station):
        print(f"Error: Station file not found: {args.station}")
        exit(1)
    
    # Create map
    plot_map(args.catalog, args.station, args.output,
             args.lat_range, args.lon_range, args.depth_range)
    
    plt.show()

