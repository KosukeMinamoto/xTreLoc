#!/usr/bin/python3
# coding: utf-8

from datetime import datetime, timedelta
import numpy as np
import pandas as pd

class Syntest:
	def __init__(self, catalog_file:str):
		self.catalog_file = catalog_file
		# 格子状イベントの中心点
		self.centers = [
			(39.8, 143.3, 20),
			(39.8, 143.6, 20),
		]
		# グリッド間隔（度）
		self.dh = 0.027  # 約3km（1度≈111km）
		# 深度間隔（km）
		self.dv = 0  # km
		# 各方向のイベント数 [Lat, Lon, Dep]
		self.events = [5, 5, 1]  # Lat, Lon, Dep
		# 開始時刻
		self.start_time = datetime(2000, 1, 1, 0, 0, 0)
		# 時間ステップ（1時間）
		self.time_step = timedelta(hours=1)
		self.catalog_data = []
		pass

	def run_syntest(self)->None:
		"""格子状イベントを生成してカタログに追加"""
		event_count = 0
		for center in self.centers:
			# まずREFイベントを生成（centerを囲むように）
			event_count += self._create_ref_data(center, event_count)
			# 次にSYNイベントを生成
			event_count += self._create_syn_data(center, event_count)
		self._write_catalog()
		pass

	def _create_ref_data(self, center:tuple, start_count:int)->int:
		"""指定された中心点を囲むようにREFイベントを生成"""
		lat_center, lon_center, dep_center = center
		
		# REFイベントの数
		num_ref = 10
		# REFイベントの配置半径（度）
		ref_radius = self.dh * (self.events[0] / 2 + 1)  # SYNイベントの範囲より外側
		
		event_count = start_count
		for i in range(num_ref):
			# 円周上に均等に配置
			angle = 2 * np.pi * i / num_ref
			lat_offset = ref_radius * np.cos(angle)
			lon_offset = ref_radius * np.sin(angle) / np.cos(np.radians(lat_center))
			
			lat = lat_center + lat_offset
			lon = lon_center + lon_offset
			dep = dep_center
			
			# 時刻を計算（1時間ステップ）
			event_time = self.start_time + event_count * self.time_step
			
			# ファイル名を生成（YYMMDD.HHMMSS形式）
			file_name = event_time.strftime('%y%m%d.%H%M%S')
			file_path = f'./dat-syn/{file_name}'
			
			# カタログデータに追加（REFイベント）
			catalog_entry = {
				'time': event_time.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z',
				'lat': lat,
				'lon': lon,
				'dep': dep,
				'elat': 0.0,
				'elon': 0.0,
				'edep': 0.0,
				'res': 0.0,
				'file': file_path,
				'method': 'REF',
				'cid': 0
			}
			self.catalog_data.append(catalog_entry)
			event_count += 1
		
		return event_count - start_count
	
	def _create_syn_data(self, center:tuple, start_count:int)->int:
		"""指定された中心点周辺に格子状イベントを生成"""
		lat_center, lon_center, dep_center = center
		
		# グリッド範囲を計算
		lat_min = lat_center - self.dh * self.events[0] / 2
		lat_max = lat_center + self.dh * self.events[0] / 2
		lon_min = lon_center - self.dh * self.events[1] / 2
		lon_max = lon_center + self.dh * self.events[1] / 2
		
		# グリッドポイントを生成
		lats = np.arange(lat_min, lat_max + self.dh/2, self.dh)
		lons = np.arange(lon_min, lon_max + self.dh/2, self.dh)
		# 深度を単層（中心点の深度のみ）に設定
		deps = [dep_center]
		
		event_count = start_count
		for lat in lats:
			for lon in lons:
				for dep in deps:
					# 時刻を計算（1時間ステップ）
					event_time = self.start_time + event_count * self.time_step
					
					# ファイル名を生成（YYMMDD.HHMMSS形式）
					file_name = event_time.strftime('%y%m%d.%H%M%S')
					file_path = f'./dat-syn/{file_name}'
					
					# カタログデータに追加（真の位置のみ）
					catalog_entry = {
						'time': event_time.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z',
						'lat': lat,   # 真の位置
						'lon': lon,   # 真の位置
						'dep': dep,   # 真の位置
						'elat': 0.0,  # 真の位置なので誤差は0
						'elon': 0.0,  # 真の位置なので誤差は0
						'edep': 0.0,  # 真の位置なので誤差は0
						'res': 0.0,   # 真の位置なので残差は0
						'file': file_path,  # 時刻に基づいたファイル名
						'method': 'SYN',
						'cid': 0
					}
					self.catalog_data.append(catalog_entry)
					event_count += 1
		
		return event_count - start_count

	def _write_catalog(self)->None:
		"""カタログデータをCSVファイルに書き込む"""
		df = pd.DataFrame(self.catalog_data)
		# カラム順序を指定
		columns = ['time', 'lat', 'lon', 'dep', 'elat', 'elon', 'edep', 'res', 'file', 'method', 'cid']
		df = df[columns]
		
		# CSVファイルに書き込み
		df.to_csv(self.catalog_file, index=False, float_format='%.6f')
		print(f"カタログデータを {self.catalog_file} に書き込みました。")
		print(f"イベント数: {len(self.catalog_data)}")
		print(f"開始時刻: {self.start_time.strftime('%Y-%m-%d %H:%M:%S')}")
		print(f"終了時刻: {self.catalog_data[-1]['time']}")
		pass

if __name__ == "__main__":
	catalog_file = "catalog_syn.csv"
	syntest = Syntest(catalog_file)
	syntest.run_syntest()
