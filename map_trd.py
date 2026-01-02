#!/usr/bin/env python3
# coding: utf-8
"""
地図のPythonスクリプト
震源をTRDモードのCSVファイルから読み込み、2列3行のレイアウトで表示
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import cartopy.crs as ccrs
import cartopy.feature as cfeature
from matplotlib.colors import ListedColormap
import os

plt.style.use('bmh')

# ===============================
# カスタムカラーマップ（cool-warm、20kmを中心）
# ===============================
def create_cool_warm_colormap(center_depth=20.0, depth_range=[10, 30]):
    """20kmを中心とするcool-warmカラーマップを作成"""
    # matplotlibのcoolwarmカラーマップをベースに使用
    import matplotlib.cm as cm
    # cm.coolwarmを直接使用（最も互換性が高い）
    cmap_base = cm.coolwarm
    
    # 深度範囲を正規化（20kmを中心に）
    depth_min, depth_max = depth_range
    center_normalized = (center_depth - depth_min) / (depth_max - depth_min)
    
    # カラーマップを調整して20kmを中心にする
    n_bins = 256
    colors = []
    
    for i in range(n_bins):
        # 0から1に正規化
        x = i / (n_bins - 1)
        
        # 20kmを中心にマッピング
        # 20kmより浅い: cool側（青）
        # 20kmより深い: warm側（赤）
        if x < center_normalized:
            # cool側にマッピング
            mapped_x = x / center_normalized * 0.5  # 0から0.5にマッピング
        else:
            # warm側にマッピング
            mapped_x = 0.5 + (x - center_normalized) / (1 - center_normalized) * 0.5  # 0.5から1.0にマッピング
        
        colors.append(cmap_base(mapped_x))
    
    cmap = ListedColormap(colors)
    return cmap

# ===============================
# データ読み込み
# ===============================
def read_catalog(filename):
    """カタログCSVファイルを読み込む"""
    if not os.path.exists(filename):
        print(f"Warning: File not found: {filename}")
        return pd.DataFrame()
    df = pd.read_csv(filename)
    
    # 列名を統一（lat/lon/dep -> latitude/longitude/depth, method -> mode）
    if 'lat' in df.columns and 'latitude' not in df.columns:
        df['latitude'] = df['lat']
    if 'lon' in df.columns and 'longitude' not in df.columns:
        df['longitude'] = df['lon']
    if 'dep' in df.columns and 'depth' not in df.columns:
        df['depth'] = df['dep']
    if 'method' in df.columns and 'mode' not in df.columns:
        df['mode'] = df['method']
    
    return df

def read_station(filename):
    """観測点テーブルファイルを読み込む（スペース区切り）"""
    if not os.path.exists(filename):
        print(f"Warning: File not found: {filename}")
        return pd.DataFrame()
    # Station file format: ML06 39.671 143.712 -2879 0.910 3.185 ...
    # 列: name, latitude, longitude, elevation, ...
    df = pd.read_csv(filename, sep=r'\s+', header=None)
    # 必要な列のみを選択（列0=name, 列1=latitude, 列2=longitude）
    if len(df.columns) >= 3:
        df = df[[0, 1, 2]].copy()
        df.columns = ['name', 'latitude', 'longitude']
    else:
        return pd.DataFrame()
    return df

# ===============================
# メインのプロット関数
# ===============================
def plot_map(ax, catalog_df, station_df, lat_range, lon_range, depth_range, title="", 
             show_xlabels=True, show_ylabels=True):
    """地図をプロット"""
    
    # 地図の範囲を設定
    ax.set_extent([lon_range[0], lon_range[1], lat_range[0], lat_range[1]], 
                   crs=ccrs.PlateCarree())
    
    # 地図の特徴を追加
    ax.add_feature(cfeature.LAND, color='lightgray', alpha=0.5)
    ax.add_feature(cfeature.COASTLINE, linewidth=0.5, color='gray')
    ax.add_feature(cfeature.BORDERS, linewidth=0.5, color='gray')
    ax.add_feature(cfeature.OCEAN, color='lightblue', alpha=0.3)
    
    # グリッドラインを追加
    # sharex/shareyを使用する場合、ラベルの表示を制御する必要がある
    gl = ax.gridlines(draw_labels=True, linewidth=0.5, color='gray', 
                      alpha=0.3, linestyle='--')
    gl.top_labels = False
    gl.right_labels = False
    gl.left_labels = show_ylabels  # y軸ラベル（左側）の表示制御
    gl.bottom_labels = show_xlabels  # x軸ラベル（下側）の表示制御
    gl.xlabel_style = {'color': 'gray', 'fontsize': 8}
    gl.ylabel_style = {'color': 'gray', 'fontsize': 8}
    
    # カタログデータをプロット（震源を丸で表示）
    if len(catalog_df) > 0:
        # カラーマップを作成
        cmap = create_cool_warm_colormap(center_depth=20.0, depth_range=depth_range)
        
        # モード別にデータをプロット
        if 'mode' in catalog_df.columns:
            # TRDモードのデータをプロット（丸、深さで色分け）
            catalog_trd = catalog_df[catalog_df['mode'] == 'TRD'].copy()
            if len(catalog_trd) > 0:
                scatter_trd = ax.scatter(catalog_trd['longitude'], catalog_trd['latitude'],
                                         c=catalog_trd['depth'], s=30, cmap=cmap,
                                         vmin=depth_range[0], vmax=depth_range[1],
                                         marker='o',  # 丸
                                         edgecolors='black', linewidths=0.3,
                                         transform=ccrs.PlateCarree(), zorder=5, alpha=0.7,
                                         label='TRD')
            
            # REFモードのデータをプロット（四角、深さで色分け）
            catalog_ref = catalog_df[catalog_df['mode'] == 'REF'].copy()
            if len(catalog_ref) > 0:
                scatter_ref = ax.scatter(catalog_ref['longitude'], catalog_ref['latitude'],
                                        c=catalog_ref['depth'], s=40, cmap=cmap,
                                        vmin=depth_range[0], vmax=depth_range[1],
                                        marker='s',  # 四角
                                        edgecolors='black', linewidths=0.5,
                                        transform=ccrs.PlateCarree(), zorder=5, alpha=0.8,
                                        label='REF')
            
            # SYNモードのデータをプロット（丸、深さで色分け）
            catalog_syn = catalog_df[catalog_df['mode'] == 'SYN'].copy()
            if len(catalog_syn) > 0:
                scatter_syn = ax.scatter(catalog_syn['longitude'], catalog_syn['latitude'],
                                        c=catalog_syn['depth'], s=30, cmap=cmap,
                                        vmin=depth_range[0], vmax=depth_range[1],
                                        marker='o',  # 丸
                                        edgecolors='black', linewidths=0.3,
                                        transform=ccrs.PlateCarree(), zorder=5, alpha=0.7,
                                        label='SYN')
        else:
            # mode列がない場合は全てTRDとして扱う
            scatter = ax.scatter(catalog_df['longitude'], catalog_df['latitude'],
                               c=catalog_df['depth'], s=30, cmap=cmap,
                               vmin=depth_range[0], vmax=depth_range[1],
                               marker='o',  # 丸
                               edgecolors='black', linewidths=0.3,
                               transform=ccrs.PlateCarree(), zorder=5, alpha=0.7)
    
    # 観測点をプロット（黒の十字）
    if len(station_df) > 0:
        ax.scatter(station_df['longitude'], station_df['latitude'],
                  s=80, marker='+', color='black', linewidths=1.5,
                  transform=ccrs.PlateCarree(), zorder=6)
    
    # タイトルを設定
    if title:
        ax.set_title(title, fontsize=10, pad=5)

# ===============================
# メイン処理
# ===============================
def main():
    # パス設定
    base_dir = "demo2"
    station_file = os.path.join(base_dir, "station_2.tbl")
    
    # 各ケースのCSVファイルパス
    # 列1: refあり、列2: refなし
    # 行1: true (default_perturb)
    # 行2: small perturbation (default_perturb)
    # 行3: large perturbation (large_perturb_ref/large_perturb_noref)
    
    catalog_files = {
        # 行: small/large perturbation, 列: syn/without ref/with ref
        'small_syn': os.path.join(base_dir, "default_perturb", "dat-syn", "catalog_syn.csv"),  # small perturbation, syn
        'small_noref': os.path.join(base_dir, "default_perturb", "dat-trd", "catalog_syn_cls_trd.csv"),  # small perturbation, without ref
        'small_ref': os.path.join(base_dir, "default_perturb", "dat-trd", "catalog_syn_cls_trd.csv"),  # small perturbation, with ref
        'large_syn': os.path.join(base_dir, "large_perturb_ref", "dat-syn", "catalog_syn.csv"),  # large perturbation, syn
        'large_noref': os.path.join(base_dir, "large_perturb_noref", "dat-trd_noref", "catalog_syn_cls_trd.csv"),  # large perturbation, without ref
        'large_ref': os.path.join(base_dir, "large_perturb_ref", "dat-trd", "catalog_syn_cls_trd.csv"),  # large perturbation, with ref
    }
    
    # 観測点データを読み込み
    station_df = read_station(station_file)
    print(f"観測点数: {len(station_df)}")
    if len(station_df) > 0:
        print(f"観測点の緯度範囲: {station_df['latitude'].min():.3f} - {station_df['latitude'].max():.3f}")
        print(f"観測点の経度範囲: {station_df['longitude'].min():.3f} - {station_df['longitude'].max():.3f}")
    
    # データを読み込み
    catalogs = {}
    for key, path in catalog_files.items():
        catalogs[key] = read_catalog(path)
    
    # 緯度・経度範囲を自動計算（全データから）
    all_lons = []
    all_lats = []
    for df in catalogs.values():
        if len(df) > 0:
            if 'longitude' in df.columns:
                all_lons.extend(df['longitude'].tolist())
            if 'latitude' in df.columns:
                all_lats.extend(df['latitude'].tolist())
    
    if len(all_lons) > 0 and len(all_lats) > 0:
        lon_range = [min(all_lons) - 0.1, max(all_lons) + 0.1]
        lat_range = [min(all_lats) - 0.1, max(all_lats) + 0.1]
    else:
        # デフォルト範囲
        lat_range = [38.0, 40.5]
        lon_range = [141.5, 144.0]
    
    # 観測点の座標も範囲に含める
    if len(station_df) > 0:
        station_lats = station_df['latitude'].tolist()
        station_lons = station_df['longitude'].tolist()
        lat_range = [min(lat_range[0], min(station_lats) - 0.1), 
                     max(lat_range[1], max(station_lats) + 0.1)]
        lon_range = [min(lon_range[0], min(station_lons) - 0.1), 
                     max(lon_range[1], max(station_lons) + 0.1)]
    
    print(f"地図の緯度範囲: {lat_range[0]:.3f} - {lat_range[1]:.3f}")
    print(f"地図の経度範囲: {lon_range[0]:.3f} - {lon_range[1]:.3f}")
    
    # 深度範囲
    depth_range = [17, 23]  # 20kmを中心とする範囲
    
    # 図を作成（3列2行 = 3x2のレイアウト）
    # 行: small/large perturbation、列: syn/without ref event/with ref event
    # 横の隙間を縮めるため、幅を少し小さくする
    fig = plt.figure(figsize=(16, 12))
    
    # サブプロットのタイトル
    row_titles = ['small perturbation', 'large perturbation']  # 行（small/large）
    col_titles = ['syn', 'without ref event', 'with ref event']  # 列（syn/without ref/with ref）
    
    # 最初のサブプロットを作成（sharex, shareyの基準となる）
    ax0 = None
    
    # 各サブプロットを作成（3列2行）
    for row_idx, row_title in enumerate(row_titles):
        for col_idx, col_title in enumerate(col_titles):
            plot_idx = row_idx * 3 + col_idx + 1  # 3列2行のインデックス計算
            
            if plot_idx == 1:
                # 最初のサブプロット
                ax = fig.add_subplot(2, 3, plot_idx, projection=ccrs.PlateCarree())
                ax0 = ax
            else:
                # その後のサブプロットはsharex, shareyを設定
                ax = fig.add_subplot(2, 3, plot_idx, projection=ccrs.PlateCarree(),
                                    sharex=ax0, sharey=ax0)
            
            # データを選択（row_idx: small(0)/large(1), col_idx: syn(0)/without ref(1)/with ref(2)）
            if row_idx == 0:  # small perturbation
                if col_idx == 0:  # syn
                    key = 'small_syn'
                elif col_idx == 1:  # without ref event
                    key = 'small_noref'
                else:  # with ref event
                    key = 'small_ref'
            else:  # large perturbation
                if col_idx == 0:  # syn
                    key = 'large_syn'
                elif col_idx == 1:  # without ref event
                    key = 'large_noref'
                else:  # with ref event
                    key = 'large_ref'
            
            # タイトルはsuptitleで表示するため、各サブプロットにはタイトルなし
            title = ""
            
            # グリッドラベルの表示制御（sharex/shareyを使用する場合）
            # 最後の行（row_idx == 1）のみx軸ラベルを表示
            # 最初の列（col_idx == 0）のみy軸ラベルを表示
            show_xlabels = (row_idx == 1)  # 最後の行のみ
            show_ylabels = (col_idx == 0)  # 最初の列のみ
            
            # プロット
            plot_map(ax, catalogs[key], station_df, lat_range, lon_range, depth_range, title,
                    show_xlabels=show_xlabels, show_ylabels=show_ylabels)
    
    # レイアウトを調整（CartopyのAxesではtight_layoutが使えないため、subplots_adjustを使用）
    # カラーバーのスペースを小さくして、地図を大きく表示
    # hspace（行間）とwspace（列間）を小さくして地図間の距離を縮める
    # 3列2行のレイアウトに合わせて調整
    # 横の隙間を縮めるため、wspaceをさらに小さく
    # suptitle用のスペースを確保
    plt.subplots_adjust(left=0.08, right=0.95, top=0.92, bottom=0.06, hspace=0.15, wspace=0.05)
    
    # 列のラベル（refのありなし）を追加
    col_labels = ['syn', 'without ref event', 'with ref event']
    for col_idx, col_label in enumerate(col_labels):
        # 各列の中央位置を計算
        x_pos = 0.08 + (col_idx + 0.5) * (0.95 - 0.08) / 3
        fig.text(x_pos, 0.96, col_label, ha='center', va='top', fontsize=12, fontweight='bold')
    
    # 行のラベル（perturbationの大小）を追加
    row_labels = ['small perturbation', 'large perturbation']
    for row_idx, row_label in enumerate(row_labels):
        # 各行の中央位置を計算（下から上へ）
        y_pos = 0.06 + (1.5 - row_idx) * (0.92 - 0.06) / 2
        fig.text(0.02, y_pos, row_label, ha='right', va='center', fontsize=12, fontweight='bold', rotation=90)
    
    # カラーバーを追加（図全体の下、水平）
    # カラーマップを作成
    cmap = create_cool_warm_colormap(center_depth=20.0, depth_range=depth_range)
    sm = plt.cm.ScalarMappable(cmap=cmap, norm=plt.Normalize(vmin=depth_range[0], vmax=depth_range[1]))
    sm.set_array([])
    # 図全体の下に水平カラーバーを追加（小さめに設定、下に下げるためpadを大きく）
    cbar = fig.colorbar(sm, ax=fig.axes, orientation='horizontal', 
                       pad=0.03, shrink=0.5, aspect=40, location='bottom')
    cbar.set_label('Depth [km]', fontsize=9)
    cbar.set_ticks(np.arange(depth_range[0], depth_range[1] + 1, 2))
    cbar.ax.tick_params(labelsize=8)
    
    # 保存
    output_file = "map_trd.png"
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"地図を {output_file} に保存しました。")
    
    # 表示
    plt.show()

if __name__ == '__main__':
    main()

