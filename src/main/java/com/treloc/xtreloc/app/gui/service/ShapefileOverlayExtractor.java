package com.treloc.xtreloc.app.gui.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.Feature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.crs.GeographicCRS;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Turns the features of a shapefile layer into lon/lat polylines clipped to a region, so that the 3-D map can draw
 * the same shapefiles that are shown on the 2-D map. Polygons contribute their rings, points a single vertex.
 */
public final class ShapefileOverlayExtractor {

    private static final Logger logger = Logger.getLogger(ShapefileOverlayExtractor.class.getName());

    /** Upper limit of returned vertices (memory / drawing cost guard). */
    public static final int MAX_VERTICES = 300_000;

    private ShapefileOverlayExtractor() {}

    /**
     * @return polylines as flat {@code {lon0, lat0, lon1, lat1, ...}} arrays (a point has length 2); the rest of
     *         the data is cut at the region border
     */
    public static List<double[]> extract(FeatureSource<?, ?> source, double lonMin, double lonMax,
                                         double latMin, double latMax) throws IOException {
        List<double[]> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        MathTransform transform = null;
        CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
        if (crs != null && !(crs instanceof GeographicCRS)) {
            try {
                transform = CRS.findMathTransform(crs, DefaultGeographicCRS.WGS84, true);
            } catch (Exception e) {
                logger.log(Level.FINE, "No transform to WGS84 for shapefile CRS; assuming lon/lat: " + e.getMessage());
            }
        }
        int[] budget = {MAX_VERTICES};
        FeatureCollection<?, ?> fc = source.getFeatures();
        try (FeatureIterator<?> it = fc.features()) {
            while (it.hasNext() && budget[0] > 0) {
                Feature f = it.next();
                Object gv = f.getDefaultGeometryProperty() == null ? null : f.getDefaultGeometryProperty().getValue();
                if (!(gv instanceof Geometry)) {
                    continue;
                }
                Geometry g = (Geometry) gv;
                if (transform != null) {
                    try {
                        g = JTS.transform(g, transform);
                    } catch (Exception e) {
                        continue;
                    }
                }
                org.locationtech.jts.geom.Envelope env = g.getEnvelopeInternal();
                if (env.getMaxX() < lonMin || env.getMinX() > lonMax || env.getMaxY() < latMin
                    || env.getMinY() > latMax) {
                    continue;
                }
                collect(g, lonMin, lonMax, latMin, latMax, out, budget);
            }
        }
        return out;
    }

    private static void collect(Geometry g, double x0, double x1, double y0, double y1,
                                List<double[]> out, int[] budget) {
        for (int i = 0; i < g.getNumGeometries(); i++) {
            Geometry part = g.getGeometryN(i);
            if (part instanceof Polygon) {
                Polygon p = (Polygon) part;
                clipLine(p.getExteriorRing(), x0, x1, y0, y1, out, budget);
                for (int r = 0; r < p.getNumInteriorRing(); r++) {
                    clipLine(p.getInteriorRingN(r), x0, x1, y0, y1, out, budget);
                }
            } else if (part instanceof LineString) {
                clipLine((LineString) part, x0, x1, y0, y1, out, budget);
            } else if (part instanceof Point) {
                Coordinate c = ((Point) part).getCoordinate();
                if (c != null && c.x >= x0 && c.x <= x1 && c.y >= y0 && c.y <= y1 && budget[0] > 0) {
                    out.add(new double[] {c.x, c.y});
                    budget[0]--;
                }
            } else if (part.getNumGeometries() > 1) {
                collect(part, x0, x1, y0, y1, out, budget);
            }
        }
    }

    /** Liang-Barsky clipping of every segment; consecutive visible segments form one polyline. */
    private static void clipLine(LineString ls, double x0, double x1, double y0, double y1,
                                 List<double[]> out, int[] budget) {
        Coordinate[] cs = ls.getCoordinates();
        List<Double> run = new ArrayList<>();
        for (int i = 0; i + 1 < cs.length && budget[0] > 0; i++) {
            double[] seg = clipSegment(cs[i].x, cs[i].y, cs[i + 1].x, cs[i + 1].y, x0, x1, y0, y1);
            if (seg == null) {
                flush(run, out, budget);
                continue;
            }
            boolean continues = !run.isEmpty()
                && run.get(run.size() - 2) == seg[0] && run.get(run.size() - 1) == seg[1];
            if (!continues) {
                flush(run, out, budget);
                run.add(seg[0]);
                run.add(seg[1]);
            }
            run.add(seg[2]);
            run.add(seg[3]);
            // when the segment end was clipped the next segment cannot continue the run
            if (seg[2] != cs[i + 1].x || seg[3] != cs[i + 1].y) {
                flush(run, out, budget);
            }
        }
        flush(run, out, budget);
    }

    private static void flush(List<Double> run, List<double[]> out, int[] budget) {
        if (run.size() >= 4) {
            double[] a = new double[run.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = run.get(i);
            }
            out.add(a);
            budget[0] -= a.length / 2;
        }
        run.clear();
    }

    /** @return clipped {@code {xa, ya, xb, yb}} or {@code null} if the segment is outside */
    static double[] clipSegment(double xa, double ya, double xb, double yb,
                                double xmin, double xmax, double ymin, double ymax) {
        double dx = xb - xa;
        double dy = yb - ya;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {xa - xmin, xmax - xa, ya - ymin, ymax - ya};
        double u1 = 0.0;
        double u2 = 1.0;
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) {
                    return null;
                }
            } else {
                double t = q[i] / p[i];
                if (p[i] < 0.0) {
                    if (t > u2) {
                        return null;
                    }
                    if (t > u1) {
                        u1 = t;
                    }
                } else {
                    if (t < u1) {
                        return null;
                    }
                    if (t < u2) {
                        u2 = t;
                    }
                }
            }
        }
        double ax = u1 == 0.0 ? xa : xa + u1 * dx;
        double ay = u1 == 0.0 ? ya : ya + u1 * dy;
        double bx = u2 == 1.0 ? xb : xa + u2 * dx;
        double by = u2 == 1.0 ? yb : ya + u2 * dy;
        return new double[] {ax, ay, bx, by};
    }
}
