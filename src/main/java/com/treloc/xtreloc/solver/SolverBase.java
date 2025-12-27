package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.io.StationRepository;
import edu.sc.seis.TauP.TauModelException;

/**
 * Base class for hypocenter location solvers.
 * Provides common functionality for loading station data and reading .dat files.
 * 
 * @author K.M.
 * @version 0.1
 * @since 2025-02-22
 */
public abstract class SolverBase extends HypoUtils {
    private static final Logger logger = Logger.getLogger(SolverBase.class.getName());
    
    protected double[][] stationTable;
    protected String[] codeStrings;
    protected double stnBottom;
    protected double threshold;
    
    /**
     * Constructs a SolverBase object with the specified configuration.
     * 
     * @param appConfig the application configuration
     * @throws TauModelException if there is an error loading the TauP model
     */
    protected SolverBase(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        this.threshold = appConfig.threshold;
        loadStationData(appConfig);
    }
    
    /**
     * Loads station data from the configuration file.
     * 
     * @param appConfig the application configuration
     * @throws RuntimeException if station data cannot be loaded
     */
    protected void loadStationData(AppConfig appConfig) {
        try {
            if (appConfig.stationFile != null) {
                StationRepository stationRepo = StationRepository.load(Path.of(appConfig.stationFile));
                this.stationTable = stationRepo.getStationTable();
                this.codeStrings = stationRepo.getCodes();
                this.stnBottom = stationRepo.getBottomDepth();
            } else {
                throw new IllegalArgumentException("Station file is not specified in config");
            }
        } catch (Exception e) {
            logger.severe("Failed to load station data: " + e.getMessage());
            throw new RuntimeException("Failed to load station data", e);
        }
    }
    
    /**
     * Loads a Point from a .dat file.
     * 
     * @param datFile the path to the .dat file
     * @return the Point object from the file
     * @throws IOException if there is an error reading the file
     */
    protected Point loadPointFromDatFile(String datFile) throws IOException {
        PointsHandler handler = new PointsHandler();
        handler.readDatFile(datFile, codeStrings, threshold);
        return handler.getMainPoint();
    }
}

