package com.treloc.xtreloc.util;

import java.io.File;

/**
 * Utility class for generating catalog file names based on processing history.
 * 
 * Naming convention: all modes append a lowercase mode suffix before .csv.
 * - SYN: catalog_syn.csv
 * - GRD / LMO / MCMC / DE / TRD: catalog_<mode>.csv (e.g. catalog_lmo.csv), or if input is given, <input_base>_<mode>.csv
 * - CLS: <input_base>_cls.csv (e.g. catalog_lmo.csv → catalog_lmo_cls.csv)
 */
public class CatalogFileNameGenerator {
    
    private static final String DEFAULT_BASE_NAME = "catalog";
    
    /**
     * Generates catalog file name based on input catalog file and current mode.
     * 
     * @param inputCatalogFile input catalog file path (can be null for SYN mode)
     * @param mode current processing mode (SYN, GRD, LMO, MCMC, TRD, CLS)
     * @param outputDir output directory
     * @return File object with appropriate catalog file name
     */
    public static File generateCatalogFileName(String inputCatalogFile, String mode, File outputDir) {
        String modeSuffix = getModeSuffix(mode);
        
        if (mode.equals("SYN")) {
            return new File(outputDir, DEFAULT_BASE_NAME + "_syn.csv");
        }
        
        String baseName = DEFAULT_BASE_NAME;
        if (inputCatalogFile != null && !inputCatalogFile.isEmpty()) {
            File inputFile = new File(inputCatalogFile);
            String inputFileName = inputFile.getName();
            
            if (inputFileName.toLowerCase().endsWith(".csv")) {
                String nameWithoutExt = inputFileName.substring(0, inputFileName.length() - 4);
                baseName = nameWithoutExt;
            } else {
                baseName = inputFileName;
            }
        }
        
        if (!baseName.contains("_" + modeSuffix)) {
            baseName = baseName + "_" + modeSuffix;
        }
        
        return new File(outputDir, baseName + ".csv");
    }
    
    /**
     * Converts mode name to lowercase suffix.
     * 
     * @param mode mode name (SYN, GRD, LMO, MCMC, TRD, CLS)
     * @return lowercase mode suffix
     */
    private static String getModeSuffix(String mode) {
        if (mode == null) {
            return "";
        }
        return mode.toLowerCase();
    }
}

