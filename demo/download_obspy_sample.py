from obspy.clients.fdsn import Client
from obspy import UTCDateTime
from obspy.geodetics import gps2dist_azimuth
import pandas as pd
import os

# Selected event with 71 stations:
#   Event: 2025-08-15T16:43:29.010000Z  Lat=33.676, Lon=-116.799, Depth=17.3 km
# Number of stations found: 71
# Station table written to demo_event/station.tbl
# Station table (CSV) also written to stations.csv
# Failed for AZ.RRSP: No data available for request.
# HTTP Status code: 204
# Detailed response of server:

# ==========================
# 設定
# ==========================
client = Client("IRIS")

# 小さめ地震（M3前後）
t_start = UTCDateTime("2025-06-01")
t_end   = UTCDateTime("2025-12-31")
minmag = 2.9
maxmag = 3.1

radius_km = 50.0
radius_deg = radius_km / 111.19  # km → degree

waveform_length = 180  # 秒（P波前後を見る用）
pre_time = 20          # 発震前20秒

outdir = "demo_event"
os.makedirs(outdir, exist_ok=True)

# ==========================
# 1. イベント検索と観測点数の確認
# ==========================
cat = client.get_events(starttime=t_start, endtime=t_end,
                        minmagnitude=minmag, maxmagnitude=maxmag)

print(f"Found {len(cat)} events")

# 各イベントについて観測点数を確認
event_station_counts = []
for i, event in enumerate(cat):
    try:
        origin = event.preferred_origin() or event.origins[0]
        ev_lat = origin.latitude
        ev_lon = origin.longitude
        ev_time = origin.time
        
        # 観測点を検索
        inv = client.get_stations(latitude=ev_lat,
                                  longitude=ev_lon,
                                  maxradius=radius_deg,
                                  channel="BH?,HH?",
                                  level="station",
                                  starttime=ev_time - 60,
                                  endtime=ev_time + 600)
        
        num_stations = len(inv.get_contents()['stations'])
        event_station_counts.append((i, event, num_stations, ev_lat, ev_lon, ev_time))
        
        if (i + 1) % 10 == 0:
            print(f"  Checked {i + 1}/{len(cat)} events...")
    except Exception as e:
        print(f"  Error checking event {i}: {e}")
        continue

# 観測点数が多い順にソート
event_station_counts.sort(key=lambda x: x[2], reverse=True)

if len(event_station_counts) == 0:
    print("ERROR: No events with stations found")
    exit(1)

# 観測点が最も多いイベントを選択
best_idx, event, num_stations, ev_lat, ev_lon, ev_time = event_station_counts[0]
origin = event.preferred_origin() or event.origins[0]
ev_dep = origin.depth / 1000.0  # km

print(f"\nSelected event with {num_stations} stations:")
print(f"  Event: {ev_time}  Lat={ev_lat:.3f}, Lon={ev_lon:.3f}, Depth={ev_dep:.1f} km")

# ==========================
# 2. 選択したイベントの観測点検索
# ==========================
inv = client.get_stations(latitude=ev_lat,
                          longitude=ev_lon,
                          maxradius=radius_deg,
                          channel="BH?,HH?",
                          level="station",
                          starttime=ev_time - 60,
                          endtime=ev_time + 600)

print(f"Number of stations found: {len(inv.get_contents()['stations'])}")

# ==========================
# 3. 観測点テーブル作成（station.tbl形式）
# ==========================
station_rows = []

for net in inv:
    for sta in net:
        dist_m, az, baz = gps2dist_azimuth(
            ev_lat, ev_lon, sta.latitude, sta.longitude)

        station_rows.append({
            "network": net.code,
            "station": sta.code,
            "latitude": sta.latitude,
            "longitude": sta.longitude,
            "elevation_m": sta.elevation,
            "distance_km": dist_m / 1000.0,
            "azimuth_deg": az,
            "backazimuth_deg": baz
        })

df_sta = pd.DataFrame(station_rows)
df_sta = df_sta.sort_values("distance_km")

# station.tbl形式で出力
# フォーマット: STATION_NAME LATITUDE LONGITUDE DEPTH P_VELOCITY S_VELOCITY
default_p_vel = 0
default_s_vel = 0

station_tbl_path = f"{outdir}/station.tbl"
with open(station_tbl_path, 'w') as f:
    for _, row in df_sta.iterrows():
        station_name = f"{row['network']}.{row['station']}"[:6]  # 最大6文字
        lat = row['latitude']
        lon = row['longitude']
        elev = row['elevation_m']
        depth = -elev if elev > 0 else -1000  # 標高を負の深度に変換
        
        # フォーマット: STATION_NAME LATITUDE LONGITUDE DEPTH P_VELOCITY S_VELOCITY
        f.write(f"{station_name:6s} {lat:8.2f} {lon:9.2f} {depth:7.0f} {default_p_vel:5.2f} {default_s_vel:5.2f}\n")

print(f"Station table written to {station_tbl_path}")

# CSV形式も出力（参考用）
df_sta.to_csv(f"{outdir}/stations.csv", index=False)
print(f"Station table (CSV) also written to stations.csv")

# ==========================
# 4. 波形取得（3成分）
# ==========================
t1 = ev_time - pre_time
t2 = ev_time + waveform_length

for _, row in df_sta.iterrows():
    try:
        st = client.get_waveforms(
            network=row["network"],
            station=row["station"],
            location="*",
            channel="BH?,HH?",
            starttime=t1,
            endtime=t2
        )

        # SACヘッダに震源情報を入れる
        for tr in st:
            tr.stats.sac = {}
            tr.stats.sac["evla"] = ev_lat
            tr.stats.sac["evlo"] = ev_lon
            tr.stats.sac["evdp"] = ev_dep
            tr.stats.sac["stla"] = row["latitude"]
            tr.stats.sac["stlo"] = row["longitude"]
            tr.stats.sac["dist"] = row["distance_km"]

        fname = f"{outdir}/{row['network']}.{row['station']}.sac"
        st.write(fname, format="SAC")
        print(f"Saved {fname}")

    except Exception as e:
        print(f"Failed for {row['network']}.{row['station']}: {e}")

print("Done.")
