#! /usr/bin/env python3
# -- coding: utf-8 --

import argparse
from datetime import datetime
from glob import glob
from tqdm import tqdm
import os
import pandas as pd
import warnings

'''
Summarize dat files in the directory and write to catalog.list

Usage:
	python3 sumdat.py --dat_dir <dat_dir>
'''

def get_args():
	parser = argparse.ArgumentParser()
	parser.add_argument(
		"--dat_dir", "-d",
		type=str,
		required=True,
		help="Directory of dat files")
	return parser.parse_args()

def sumdat(dat_dir: str) -> pd.DataFrame:
	if os.path.exists("catalog.list"):
		print("Do you want to overwrite catalog.list? (y/n)")
		ans = input()
		if ans.lower() != "y":
			warnings.warn("catalog.list already exists. Exiting...")
			exit(0)
	dat_files = glob(os.path.join(dat_dir, "*"))
	catalog = []
	for dat_file in tqdm(dat_files):
		tqdm.write(dat_file)
		df = pd.read_csv(dat_file, sep="\s+", header=None)
		date = datetime.strptime(dat_file.split("/")[-1], "%y%m%d.%H%M%S")
		catalog.append([
			date,
			df.iat[0, 0], # lat
			df.iat[0, 1], # lon
			df.iat[0, 2], # dep
			df.iat[1, 0], # elat
			df.iat[1, 1], # elon
			df.iat[1, 2], # edep
			df.iat[1, 3], # res
			dat_file,
			df.iat[0, 3], # type
		])
	df = pd.DataFrame(catalog, columns=["time", "lat", "lon", "dep", "elat", "elon", "edep", "res", "type", "file"])
	df.sort_values(by=["time"], inplace=True)
	df.to_csv("catalog.list", index=False, header=False, sep=" ", date_format="%Y-%m-%dT%H:%M:%S")
	return df

if __name__ == "__main__":
	args = get_args()
	dat_dir = args.dat_dir
	df = sumdat(dat_dir)
