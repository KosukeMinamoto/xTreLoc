package com.treloc.xtreloc.io;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * I/O utility for triple difference data.
 * Supports both binary and CSV formats.
 * 
 * Binary format structure:
 * - Header: int (number of records)
 * - For each record:
 *   - int (eve0)
 *   - int (eve1)
 *   - int (stn0)
 *   - int (stn1)
 *   - double (tdTime)
 *   - double (distKm)
 *   - int (clusterId)
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public class TripleDifferenceIO {
    private static final Logger logger = Logger.getLogger(TripleDifferenceIO.class.getName());
    
    /**
     * Triple difference record structure.
     */
    public static class TripleDifference {
        public final int eve0;
        public final int eve1;
        public final int stn0;
        public final int stn1;
        public final double tdTime;
        public final double distKm;
        public final int clusterId;
        
        public TripleDifference(int eve0, int eve1, int stn0, int stn1, 
                               double tdTime, double distKm, int clusterId) {
            this.eve0 = eve0;
            this.eve1 = eve1;
            this.stn0 = stn0;
            this.stn1 = stn1;
            this.tdTime = tdTime;
            this.distKm = distKm;
            this.clusterId = clusterId;
        }
    }
    
    /**
     * Saves triple differences to a binary file.
     * 
     * @param tripleDifferences the list of triple differences
     * @param outputFile the output file path
     * @throws IOException if I/O error occurs
     */
    public static void saveBinary(List<TripleDifference> tripleDifferences, File outputFile) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputFile)))) {
            dos.writeInt(tripleDifferences.size());
            
            for (TripleDifference td : tripleDifferences) {
                dos.writeInt(td.eve0);
                dos.writeInt(td.eve1);
                dos.writeInt(td.stn0);
                dos.writeInt(td.stn1);
                dos.writeDouble(td.tdTime);
                dos.writeDouble(td.distKm);
                dos.writeInt(td.clusterId);
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
    public static List<TripleDifference> loadBinary(File inputFile) throws IOException {
        List<TripleDifference> tripleDifferences = new ArrayList<>();
        
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(inputFile)))) {
            int numRecords = dis.readInt();
            
            for (int i = 0; i < numRecords; i++) {
                int eve0 = dis.readInt();
                int eve1 = dis.readInt();
                int stn0 = dis.readInt();
                int stn1 = dis.readInt();
                double tdTime = dis.readDouble();
                double distKm = dis.readDouble();
                int clusterId = dis.readInt();
                
                tripleDifferences.add(new TripleDifference(eve0, eve1, stn0, stn1, 
                                                           tdTime, distKm, clusterId));
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
            writer.println("eve0,eve1,stn0,stn1,tdTime,distKm,clusterId");
            for (TripleDifference td : tripleDifferences) {
                writer.printf("%d,%d,%d,%d,%.3f,%.3f,%d%n", 
                    td.eve0, td.eve1, td.stn0, td.stn1, td.tdTime, td.distKm, td.clusterId);
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
            
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.trim().split(",");
                if (parts.length >= 7) {
                    int eve0 = Integer.parseInt(parts[0].trim());
                    int eve1 = Integer.parseInt(parts[1].trim());
                    int stn0 = Integer.parseInt(parts[2].trim());
                    int stn1 = Integer.parseInt(parts[3].trim());
                    double tdTime = Double.parseDouble(parts[4].trim());
                    double distKm = Double.parseDouble(parts[5].trim());
                    int clusterId = Integer.parseInt(parts[6].trim());
                    
                    tripleDifferences.add(new TripleDifference(eve0, eve1, stn0, stn1, 
                                                               tdTime, distKm, clusterId));
                }
            }
        }
        logger.info("Loaded " + tripleDifferences.size() + " triple differences from CSV file: " + inputFile.getAbsolutePath());
        return tripleDifferences;
    }
}

