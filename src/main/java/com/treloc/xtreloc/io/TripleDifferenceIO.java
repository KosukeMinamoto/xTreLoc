package com.treloc.xtreloc.io;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * I/O utility for triple difference data.
 * Supports both binary and CSV formats.
 *
 * Binary format v1 (legacy): int (count), then per record: eve0, eve1, stn0, stn1, tdTime, distKm, clusterId.
 * Binary format v2: int magic (0x54444602 "TDF2"), int (count), then per record: eve0, eve1, stn0, stn1, tdTime, distKm, clusterId, residual.
 *
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class TripleDifferenceIO {
    private static final Logger logger = Logger.getLogger(TripleDifferenceIO.class.getName());

    /** Magic for binary format v2 (with residual). Enables backward-compatible loading of v1. */
    private static final int BINARY_MAGIC_V2 = 0x54444602; // "TDF2" in ASCII

    /**
     * Triple difference record structure.
     * residual: |observed_td - calculated_td| (sec). Double.NaN if not computed (e.g. legacy file).
     */
    public static class TripleDifference {
        public final int eve0;
        public final int eve1;
        public final int stn0;
        public final int stn1;
        public final double tdTime;
        public final double distKm;
        public final int clusterId;
        /** Residual in seconds (absolute value). Double.NaN when not available. */
        public final double residual;

        public TripleDifference(int eve0, int eve1, int stn0, int stn1,
                               double tdTime, double distKm, int clusterId) {
            this(eve0, eve1, stn0, stn1, tdTime, distKm, clusterId, Double.NaN);
        }

        public TripleDifference(int eve0, int eve1, int stn0, int stn1,
                               double tdTime, double distKm, int clusterId, double residual) {
            this.eve0 = eve0;
            this.eve1 = eve1;
            this.stn0 = stn0;
            this.stn1 = stn1;
            this.tdTime = tdTime;
            this.distKm = distKm;
            this.clusterId = clusterId;
            this.residual = residual;
        }
    }
    
    /**
     * Saves triple differences to a binary file.
     * 
     * @param tripleDifferences the list of triple differences
     * @param outputFile the output file path
     * @throws IOException if I/O error occurs
     */
    /**
     * Saves triple differences in binary format v2 (with residual).
     * Use loadBinary to read; v1 files (no residual) are still supported.
     */
    public static void saveBinary(List<TripleDifference> tripleDifferences, File outputFile) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            dos.writeInt(BINARY_MAGIC_V2);
            dos.writeInt(tripleDifferences.size());

            for (TripleDifference td : tripleDifferences) {
                dos.writeInt(td.eve0);
                dos.writeInt(td.eve1);
                dos.writeInt(td.stn0);
                dos.writeInt(td.stn1);
                dos.writeDouble(td.tdTime);
                dos.writeDouble(td.distKm);
                dos.writeInt(td.clusterId);
                dos.writeDouble(Double.isNaN(td.residual) ? Double.NaN : td.residual);
            }
        }
        logger.info("Saved " + tripleDifferences.size() + " triple differences to binary file: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Loads triple differences from a binary file.
     * 
     * @param inputFile the input file path
     * @return list of triple differences
     * @throws IOException if I/O error occurs
     */
    /**
     * Loads triple differences from binary file.
     * Supports v1 (no magic, 7 fields) and v2 (magic TDF2, 8 fields with residual).
     */
    public static List<TripleDifference> loadBinary(File inputFile) throws IOException {
        List<TripleDifference> tripleDifferences = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(inputFile)))) {
            int firstInt = dis.readInt();
            int numRecords;
            boolean hasResidual;

            if (firstInt == BINARY_MAGIC_V2) {
                numRecords = dis.readInt();
                hasResidual = true;
            } else {
                numRecords = firstInt;
                hasResidual = false;
            }

            for (int i = 0; i < numRecords; i++) {
                int eve0 = dis.readInt();
                int eve1 = dis.readInt();
                int stn0 = dis.readInt();
                int stn1 = dis.readInt();
                double tdTime = dis.readDouble();
                double distKm = dis.readDouble();
                int clusterId = dis.readInt();
                double residual = hasResidual ? dis.readDouble() : Double.NaN;

                tripleDifferences.add(new TripleDifference(eve0, eve1, stn0, stn1,
                        tdTime, distKm, clusterId, residual));
            }
        }
        logger.info("Loaded " + tripleDifferences.size() + " triple differences from binary file: " + inputFile.getAbsolutePath());
        return tripleDifferences;
    }
    
    /**
     * Saves triple differences to a CSV file.
     * 
     * @param tripleDifferences the list of triple differences
     * @param outputFile the output file path
     * @throws IOException if I/O error occurs
     */
    public static void saveCSV(List<TripleDifference> tripleDifferences, File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            writer.println("eve0,eve1,stn0,stn1,tdTime,distKm,clusterId,residual");
            for (TripleDifference td : tripleDifferences) {
                String resStr = Double.isNaN(td.residual) ? "" : String.format("%.6e", td.residual);
                writer.printf("%d,%d,%d,%d,%.3f,%.3f,%d,%s%n",
                    td.eve0, td.eve1, td.stn0, td.stn1, td.tdTime, td.distKm, td.clusterId, resStr);
            }
        }
        logger.info("Saved " + tripleDifferences.size() + " triple differences to CSV file: " + outputFile.getAbsolutePath());
    }
    
    /**
     * Loads triple differences from a CSV file.
     * 
     * @param inputFile the input file path
     * @return list of triple differences
     * @throws IOException if I/O error occurs
     */
    public static List<TripleDifference> loadCSV(File inputFile) throws IOException {
        List<TripleDifference> tripleDifferences = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
            String line = br.readLine(); // Skip header
            if (line == null || !line.contains("eve0")) {
                throw new IOException("Invalid CSV header");
            }
            boolean hasResidualColumn = line.contains("residual");

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.trim().split(",", -1);
                if (parts.length >= 7) {
                    int eve0 = Integer.parseInt(parts[0].trim());
                    int eve1 = Integer.parseInt(parts[1].trim());
                    int stn0 = Integer.parseInt(parts[2].trim());
                    int stn1 = Integer.parseInt(parts[3].trim());
                    double tdTime = Double.parseDouble(parts[4].trim());
                    double distKm = Double.parseDouble(parts[5].trim());
                    int clusterId = Integer.parseInt(parts[6].trim());
                    double residual = Double.NaN;
                    if (hasResidualColumn && parts.length >= 8 && parts[7] != null && !parts[7].trim().isEmpty()) {
                        try {
                            residual = Double.parseDouble(parts[7].trim());
                        } catch (NumberFormatException e) {
                            logger.log(Level.FINE, "Skip invalid residual column value: " + parts[7].trim(), e);
                        }
                    }
                    tripleDifferences.add(new TripleDifference(eve0, eve1, stn0, stn1,
                            tdTime, distKm, clusterId, residual));
                }
            }
        }
        logger.info("Loaded " + tripleDifferences.size() + " triple differences from CSV file: " + inputFile.getAbsolutePath());
        return tripleDifferences;
    }
}

