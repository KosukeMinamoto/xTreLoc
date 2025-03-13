#!/bin/bash

# Check truth data
gnuplot -e "filename='catalog_ground_truth.list'" map_njt.plt

# Generate synthetic data
mkdir -p dat-SYN
./xtreloc SYN
python3 sumdat.py -d dat-SYN
mv catalog.list catalog_syn.list
gnuplot -e "filename='catalog_syn.list'" map_njt.plt

# Station-pair DD
./xtreloc STD
python3 sumdat.py -d dat-SYN_STD
mv catalog.list catalog_syn_std.list
gnuplot -e "filename='catalog_syn_std.list'" map_njt.plt

# Clustering
./xtreloc CLS

# Relocation
./xtreloc TRD
gnuplot -e "filename='catalog_syn_TRD.list'" map_njt.plt