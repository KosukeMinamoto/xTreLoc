#!/bin/sh

# Create datasets
mkdir -p dat-syn
./xtreloc SYN

# Station-pair DD
mkdir -p dat-syn-std
./xtreloc STD

# Summarize STD results into 'catalog.list'
python3 sumdat.py --dat_dir dat-syn-std

# Clustering & calc triple-diff times
./xtreloc CLS

# Triple Difference
mkdir -p dat-syn-std-trd
./xtreloc TRD

./xtreloc SEE
