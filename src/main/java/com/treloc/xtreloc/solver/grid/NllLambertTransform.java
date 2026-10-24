package com.treloc.xtreloc.solver.grid;

import java.util.Locale;
import java.util.Objects;

/**
 * NonLinLoc {@code TRANSFORM LAMBERT}: GMT / Snyder Lambert conformal conic as in {@code map_project.c}
 * ({@code vlamb} / {@code lamb} / {@code ilamb}) plus the GridLib clockwise rotation and m→km conversion.
 */
public final class NllLambertTransform implements NllGeographicTransform {

    private static final double D2R = Math.PI / 180.0;
    private static final double R2D = 180.0 / Math.PI;
    private static final double SMALL = 1.0e-10;

    private static final class Ellipsoid {
        final String name;
        final double eqRadiusM;
        final double flattening;

        Ellipsoid(String name, double eqRadiusM, double flattening) {
            this.name = name;
            this.eqRadiusM = eqRadiusM;
            this.flattening = flattening;
        }
    }

    /** Same table as NonLinLoc {@code map_project.c}. */
    private static final Ellipsoid[] ELLIPSOIDS = {
        new Ellipsoid("WGS-84", 6378137.0, 1.0 / 298.254),
        new Ellipsoid("GRS-80", 6378137.0, 1.0 / 298.257),
        new Ellipsoid("WGS-72", 6378135.0, 1.0 / 298.26),
        new Ellipsoid("Australian", 6378160.0, 1.0 / 298.25),
        new Ellipsoid("Krasovsky", 6378245.0, 1.0 / 298.3),
        new Ellipsoid("International", 6378388.0, 1.0 / 297.0),
        new Ellipsoid("Hayford-1909", 6378388.0, 1.0 / 297.0),
        new Ellipsoid("Clarke-1880", 6378249.1, 1.0 / 293.46),
        new Ellipsoid("Clarke-1866", 6378206.4, 1.0 / 294.98),
        new Ellipsoid("Airy", 6377563.4, 1.0 / 299.32),
        new Ellipsoid("Bessel", 6377397.2, 1.0 / 299.15),
        new Ellipsoid("Hayford-1830", 6377276.3, 1.0 / 300.80),
        new Ellipsoid("Sphere", 6371008.7714, 0.0),
        new Ellipsoid("NAD-27", 6378206.4, 1.0 / 294.98),
        new Ellipsoid("NAD-83", 6378137.4, 1.0 / 294.26)
    };

    private final String ellipsoid;
    private final double lat0;
    private final double lon0;
    private final double phi1;
    private final double phi2;
    private final double rotCwDeg;
    private final double eqRad;
    private final double ecc;
    private final double ecc2;
    private final double n;
    private final double f;
    private final double rho0;
    private final double cosAng;
    private final double sinAng;
    private final double kmPerDegLon;
    private final double kmPerDegLat;

    public NllLambertTransform(String ellipsoidName, double lat0, double lon0,
                               double firstStdParal, double secondStdParal, double rotCwDeg) {
        Ellipsoid ell = ellipsoid(ellipsoidName);
        this.ellipsoid = ell.name;
        this.lat0 = lat0;
        this.lon0 = lon0;
        this.phi1 = firstStdParal;
        this.phi2 = secondStdParal;
        this.rotCwDeg = rotCwDeg;
        this.eqRad = ell.eqRadiusM;
        this.ecc2 = 2.0 * ell.flattening - ell.flattening * ell.flattening;
        this.ecc = Math.sqrt(Math.max(0.0, ecc2));

        double pha = firstStdParal * D2R;
        double phb = secondStdParal * D2R;
        double tPha = t(pha);
        double mPha = m(pha);
        double tPhb = t(phb);
        double mPhb = m(phb);
        double t0 = t(lat0 * D2R);
        if (pha != phb) {
            this.n = (dLog(mPha) - dLog(mPhb)) / (dLog(tPha) - dLog(tPhb));
        } else {
            this.n = Math.sin(pha);
        }
        this.f = mPha / (this.n * Math.pow(tPha, this.n));
        this.rho0 = eqRad * this.f * Math.pow(t0, this.n);
        this.cosAng = Math.cos(rotCwDeg * D2R);
        this.sinAng = Math.sin(rotCwDeg * D2R);

        double[] dlon = rawXy(lat0, lon0 + 1.0e-4);
        double[] dlat = rawXy(lat0 + 1.0e-4, lon0);
        double[] o = rawXy(lat0, lon0);
        this.kmPerDegLon = Math.abs(dlon[0] - o[0]) / 1.0e-4;
        this.kmPerDegLat = Math.abs(dlat[1] - o[1]) / 1.0e-4;
    }

    public static NllLambertTransform of(String ellipsoidName, double lat0, double lon0,
                                         double firstStdParal, double secondStdParal, double rotCwDeg) {
        return new NllLambertTransform(ellipsoidName, lat0, lon0, firstStdParal, secondStdParal, rotCwDeg);
    }

    public static boolean knownEllipsoid(String name) {
        return name != null && findEllipsoid(name) != null;
    }

    @Override
    public double lat0() { return lat0; }

    @Override
    public double lon0() { return lon0; }

    @Override
    public double kmPerDegLon() { return kmPerDegLon; }

    @Override
    public double kmPerDegLat() { return kmPerDegLat; }

    @Override
    public double[] toXY(double lat, double lon) {
        double[] raw = rawXy(lat, lon);
        return rotateFwd(raw[0], raw[1]);
    }

    @Override
    public double[] toLatLon(double xKm, double yKm) {
        double[] raw = rotateInv(xKm, yKm);
        return inverseRaw(raw[0], raw[1]);
    }

    @Override
    public String headerLine() {
        return String.format(Locale.ROOT,
            "TRANSFORM  LAMBERT RefEllipsoid %s  LatOrig  %.6f  LongOrig  %.6f  FirstStdParal  %.6f  SecondStdParal  %.6f  RotCW  %.6f",
            ellipsoid, lat0, lon0, phi1, phi2, rotCwDeg);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NllLambertTransform other)) {
            return false;
        }
        return ellipsoid.equals(other.ellipsoid)
            && Double.compare(lat0, other.lat0) == 0
            && Double.compare(lon0, other.lon0) == 0
            && Double.compare(phi1, other.phi1) == 0
            && Double.compare(phi2, other.phi2) == 0
            && Double.compare(rotCwDeg, other.rotCwDeg) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ellipsoid, lat0, lon0, phi1, phi2, rotCwDeg);
    }

    private double[] rawXy(double latDeg, double lonDeg) {
        double lon = lonDeg;
        while ((lon - lon0) < -180.0) {
            lon += 360.0;
        }
        while ((lon - lon0) > 180.0) {
            lon -= 360.0;
        }
        double lat = latDeg * D2R;
        double hold2 = Math.pow((1.0 - ecc * Math.sin(lat)) / (1.0 + ecc * Math.sin(lat)), 0.5 * ecc);
        double hold3 = Math.tan(45.0 * D2R - 0.5 * lat);
        if (Math.abs(hold3) < SMALL) {
            hold3 = 0.0;
        }
        double hold1 = hold3 == 0.0 ? 0.0 : Math.pow(hold3 / hold2, n);
        double rho = eqRad * f * hold1;
        double theta = n * (lon - lon0) * D2R;
        return new double[] {rho * Math.sin(theta) / 1000.0, (rho0 - rho * Math.cos(theta)) / 1000.0};
    }

    private double[] inverseRaw(double xKm, double yKm) {
        double x = xKm * 1000.0;
        double y = yKm * 1000.0;
        double theta = Math.atan(x / (rho0 - y));
        double lon = (theta / n) * R2D + lon0;
        double temp = x * x + (rho0 - y) * (rho0 - y);
        double rho = Math.copySign(Math.sqrt(Math.max(0.0, temp)), n);
        double tVal = Math.pow(rho / (eqRad * f), 1.0 / n);
        double tphi = 90.0 * D2R - 2.0 * Math.atan(tVal);
        double phi = tphi;
        double delta = 1.0;
        for (int i = 0; i < 100 && delta > 1.0e-8; i++) {
            temp = (1.0 - ecc * Math.sin(tphi)) / (1.0 + ecc * Math.sin(tphi));
            phi = 90.0 * D2R - 2.0 * Math.atan(tVal * Math.pow(temp, 0.5 * ecc));
            delta = Math.abs(Math.abs(tphi) - Math.abs(phi));
            tphi = phi;
        }
        if (lon < -180.0) {
            lon += 360.0;
        } else if (lon > 180.0) {
            lon -= 360.0;
        }
        return new double[] {phi * R2D, lon};
    }

    private double[] rotateFwd(double x, double y) {
        return new double[] {x * cosAng - y * sinAng, y * cosAng + x * sinAng};
    }

    private double[] rotateInv(double x, double y) {
        return new double[] {x * cosAng + y * sinAng, y * cosAng - x * sinAng};
    }

    private double t(double phiRad) {
        return Math.tan(45.0 * D2R - 0.5 * phiRad)
            / Math.pow((1.0 - ecc * Math.sin(phiRad)) / (1.0 + ecc * Math.sin(phiRad)), 0.5 * ecc);
    }

    private double m(double phiRad) {
        return Math.cos(phiRad) / Math.sqrt(1.0 - ecc2 * Math.sin(phiRad) * Math.sin(phiRad));
    }

    private static double dLog(double x) {
        return x <= 0.0 ? -1.0e10 : Math.log(x);
    }

    private static Ellipsoid ellipsoid(String name) {
        Ellipsoid e = findEllipsoid(name);
        if (e == null) {
            throw new IllegalArgumentException("Unknown NonLinLoc ellipsoid '" + name
                + "' (WGS-84, Clarke-1880, Sphere, …)");
        }
        return e;
    }

    private static Ellipsoid findEllipsoid(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String want = name.trim();
        for (Ellipsoid e : ELLIPSOIDS) {
            if (e.name.equalsIgnoreCase(want)) {
                return e;
            }
        }
        return null;
    }
}
