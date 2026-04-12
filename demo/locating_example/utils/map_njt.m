% This is a matlab demo script for plotting 
% the catalog and stations on a map.
% 

clear; clc; close all;

catalog_file = "catalog_ground_truth.csv";
station_file = "station.tbl";
latLim = [38.5, 40.5];
lonLim = [141, 144];

figure
grid on
ax = worldmap(latLim, lonLim);
setm(ax, "FFaceColor", [0.9 0.9 0.9]);

% You can get 'gshhs_f.b' from
% https://www.ngdc.noaa.gov/mgg/shorelines/data/gshhs/oldversions/version1.2/
% 
if exist('gshhs_f.b', 'file')
    nyc = gshhs('gshhs_f.b', latLim, lonLim);
    levels          = [nyc.Level];
    land            = (levels == 1);
    %lake            = (levels == 2);
    %island          = (levels == 3); % island in a lake
    %pond            = (levels == 4); % pond in an island in a lake
    %ice_front       = (levels == 5); % ice shelves around Antarctica
    %grounding_line  = (levels == 6); % land of Antarctica
    %geoshow([nyc(ice_front).Lat],      [nyc(ice_front).Lon],      'DisplayType', 'Polygon', 'FaceColor', [230/255 230/255 230/255]); % gray
    %geoshow([nyc(grounding_line).Lat], [nyc(grounding_line).Lon], 'DisplayType', 'Line',    'Color',     [255/255 105/255 180/255]); % hot pink
    geoshow([nyc(land).Lat],           [nyc(land).Lon],           'DisplayType', 'Polygon', 'FaceColor', [  0/255 100/255   0/255]); % forest green
    %geoshow([nyc(lake).Lat],           [nyc(lake).Lon],           'DisplayType', 'Polygon', 'FaceColor', [  0/255   0/255 128/255]); % navy blue
    %geoshow([nyc(island).Lat],         [nyc(island).Lon],         'DisplayType', 'Polygon', 'FaceColor', [210/255 105/255  30/255]); % chocolate
    %geoshow([nyc(pond).Lat],           [nyc(pond).Lon],           'DisplayType', 'Polygon', 'FaceColor', [ 84/255  84/255  84/255]); % light steel blue
else
    load coastlines.mat
    geoshow(coastlat, coastlon, "DisplayType", "line", "Color", "k", "LineWidth", 2);
end

hyp = readtable(catalog_file, "Filetype", "text", "Delimiter", ",");
stn = readtable(station_file, "Filetype", "text", "Delimiter", ",");

scatterm(stn.Var2, stn.Var3, 50, "+", "k");
scatterm(hyp.Var2, hyp.Var3, 50, hyp.Var4);

cb=colorbar();
ylabel(cb,"Depth [km]");
colormap parula

