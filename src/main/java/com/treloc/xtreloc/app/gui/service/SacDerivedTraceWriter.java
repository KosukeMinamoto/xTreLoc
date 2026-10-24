package com.treloc.xtreloc.app.gui.service;

import edu.sc.seis.seisFile.sac.SacHeader;
import edu.sc.seis.seisFile.sac.SacTimeSeries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a derived (e.g. CF / envelope) trace as SAC using timing and station metadata from a template trace.
 */
public final class SacDerivedTraceWriter {

    private SacDerivedTraceWriter() {}

    /**
     * @param templateHorizontal horizontal component used only for header metadata (must not be mutated)
     * @param y              sample values (same length as intended npts)
     * @param kcmpnm         up to 8 chars (e.g. CF, ENV)
     * @param outFile        destination .sac path
     */
    public static void write(SacTimeSeries templateHorizontal, double[] y, String kcmpnm, Path outFile) throws IOException {
        if (templateHorizontal == null || y == null || y.length == 0) {
            throw new IOException("invalid template or empty trace");
        }
        SacHeader src = templateHorizontal.getHeader();
        SacHeader h = new SacHeader();
        copyHeaderMetadata(h, src);

        float delta = src.getDelta();
        float[] fy = new float[y.length];
        double minV = y[0];
        double maxV = y[0];
        for (int i = 0; i < y.length; i++) {
            float v = (float) y[i];
            fy[i] = v;
            if (v < minV) {
                minV = v;
            }
            if (v > maxV) {
                maxV = v;
            }
        }
        h.setNpts(y.length);
        h.setDelta(delta);
        h.setB(0f);
        h.setE((y.length - 1) * delta);
        h.setDepmin((float) minV);
        h.setDepmax((float) maxV);
        h.setLeven(1);

        String cmp = kcmpnm == null ? "DER" : kcmpnm.trim();
        if (cmp.length() > 8) {
            cmp = cmp.substring(0, 8);
        }
        h.setKcmpnm(cmp);

        SacTimeSeries out = new SacTimeSeries(h, fy);
        if (outFile.getParent() != null) {
            Files.createDirectories(outFile.getParent());
        }
        out.write(outFile.toFile());
    }

    static void copyHeaderMetadata(SacHeader h, SacHeader src) {
        h.setNzyear(src.getNzyear());
        h.setNzjday(src.getNzjday());
        h.setNzhour(src.getNzhour());
        h.setNzmin(src.getNzmin());
        h.setNzsec(src.getNzsec());
        h.setNzmsec(src.getNzmsec());
        h.setKstnm(src.getKstnm());
        h.setStla(src.getStla());
        h.setStlo(src.getStlo());
        h.setStel(src.getStel());
        h.setStdp(src.getStdp());
        h.setEvla(src.getEvla());
        h.setEvlo(src.getEvlo());
        h.setEvdp(src.getEvdp());
        h.setEvel(src.getEvel());
        h.setDist(src.getDist());
        h.setAz(src.getAz());
        h.setBaz(src.getBaz());
        h.setCmpaz(src.getCmpaz());
        h.setCmpinc(src.getCmpinc());
        h.setIftype(src.getIftype());
        h.setIdep(src.getIdep());
        h.setIztype(src.getIztype());
        h.setIevtyp(src.getIevtyp());
    }

    static String safeFileToken(String name) {
        if (name == null || name.isEmpty()) {
            return "UNK";
        }
        return name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    static String componentSuffix(CrossCorrelationPickingService.SignalKind kind) {
        if (kind == CrossCorrelationPickingService.SignalKind.ENVELOPE) {
            return "ENV";
        }
        if (kind == CrossCorrelationPickingService.SignalKind.RAW) {
            return "RAW";
        }
        return "CF";
    }
}
