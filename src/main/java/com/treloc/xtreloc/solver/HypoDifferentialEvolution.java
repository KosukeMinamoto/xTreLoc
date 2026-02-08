package com.treloc.xtreloc.solver;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.Random;

import com.treloc.xtreloc.io.AppConfig;
import com.treloc.xtreloc.util.SolverLogger;
import com.fasterxml.jackson.databind.JsonNode;
import edu.sc.seis.TauP.TauModelException;

/**
 * Hypocenter location using Differential Evolution (DE) algorithm.
 * 
 * <p>Differential Evolution is a population-based optimization algorithm
 * that is particularly effective for continuous optimization problems.
 * It uses mutation, crossover, and selection operations to evolve a
 * population of candidate solutions toward the global optimum.
 * 
 * <p>The algorithm maintains a population of candidate hypocenters and
 * iteratively improves them by:
 * <ul>
 *   <li>Mutation: Creating a mutant vector by combining existing solutions</li>
 *   <li>Crossover: Creating a trial vector by mixing mutant and target vectors</li>
 *   <li>Selection: Keeping the better solution between trial and target</li>
 * </ul>
 * 
 * <p>This implementation follows the DE/rand/1/bin strategy:
 * <ul>
 *   <li>rand: Random selection of base vector</li>
 *   <li>1: One difference vector</li>
 *   <li>bin: Binomial crossover</li>
 * </ul>
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 * @since 2025-01-XX
 */
public class HypoDifferentialEvolution extends SolverBase {
    private static final Logger logger = Logger.getLogger(HypoDifferentialEvolution.class.getName());
    
    private double hypBottom;
    private int populationSize;  // NP: Number of individuals in population
    private int maxGenerations; // G: Maximum number of generations
    private double scalingFactor; // F: Scaling factor for mutation (typically 0.5-1.0)
    private double crossoverRate; // CR: Crossover probability (typically 0.5-1.0)
    private ConvergenceCallback convergenceCallback;
    
    /**
     * Represents an individual in the population.
     * Contains hypocenter coordinates (lat, lon, dep) and fitness value (residual).
     */
    private static class Individual {
        double lat;
        double lon;
        double dep;
        double fitness; // Residual (standard deviation of travel time residuals)
        
        Individual(double lat, double lon, double dep, double fitness) {
            this.lat = lat;
            this.lon = lon;
            this.dep = dep;
            this.fitness = fitness;
        }
        
        Individual(Individual other) {
            this.lat = other.lat;
            this.lon = other.lon;
            this.dep = other.dep;
            this.fitness = other.fitness;
        }
    }
    
    /**
     * Sets the convergence callback for reporting convergence information.
     * 
     * @param callback the convergence callback
     */
    public void setConvergenceCallback(ConvergenceCallback callback) {
        this.convergenceCallback = callback;
    }
    
    /**
     * Constructs a HypoDifferentialEvolution object with the specified configuration.
     * 
     * @param appConfig the application configuration
     * @throws TauModelException if there is an error loading the TauP model
     */
    public HypoDifferentialEvolution(AppConfig appConfig) throws TauModelException {
        super(appConfig);
        this.hypBottom = appConfig.hypBottom;
        
        // Load DE parameters from config
        JsonNode deSolver = appConfig.solver != null ? appConfig.solver.get("DE") : null;
        if (deSolver != null) {
            this.populationSize = deSolver.has("populationSize") ? 
                deSolver.get("populationSize").asInt() : 50;
            this.maxGenerations = deSolver.has("maxGenerations") ? 
                deSolver.get("maxGenerations").asInt() : 100;
            this.scalingFactor = deSolver.has("scalingFactor") ? 
                deSolver.get("scalingFactor").asDouble() : 0.8;
            this.crossoverRate = deSolver.has("crossoverRate") ? 
                deSolver.get("crossoverRate").asDouble() : 0.9;
        } else {
            // Default values
            this.populationSize = 50;
            this.maxGenerations = 100;
            this.scalingFactor = 0.8;
            this.crossoverRate = 0.9;
        }
        
        // Validate parameters
        if (populationSize < 4) {
            logger.warning("Population size must be at least 4, setting to 4");
            this.populationSize = 4;
        }
        if (scalingFactor <= 0 || scalingFactor > 2.0) {
            logger.warning("Scaling factor should be in (0, 2], using default 0.8");
            this.scalingFactor = 0.8;
        }
        if (crossoverRate < 0 || crossoverRate > 1.0) {
            logger.warning("Crossover rate should be in [0, 1], using default 0.9");
            this.crossoverRate = 0.9;
        }
        
        logger.info(String.format("DE parameters: populationSize=%d, maxGenerations=%d, scalingFactor=%.3f, crossoverRate=%.3f",
            populationSize, maxGenerations, scalingFactor, crossoverRate));
    }
    
    /**
     * Performs hypocenter location using Differential Evolution method.
     * Reads input data from a .dat file and writes the result to an output file.
     * 
     * @param datFile the input .dat file path
     * @param outFile the output .dat file path
     * @throws TauModelException if there is an error in the Tau model
     * @throws RuntimeException if file I/O fails
     */
    public void start(String datFile, String outFile) throws TauModelException {
        String fileName = new java.io.File(datFile).getName();
        logger.info("Starting Differential Evolution location for: " + fileName);
        SolverLogger.fine("Starting Differential Evolution location for: " + fileName);
        
        Point point;
        try {
            point = loadPointFromDatFile(datFile);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to read dat file in DE mode:\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to read dat file: " + datFile, e);
        }
        
        PointsHandler pointsHandler = new PointsHandler();
        pointsHandler.setMainPoint(point);
        
        double[][] lagTable = point.getLagTable();
        int[] usedIdx = point.getUsedIdx();
        String time = point.getTime();
        double initialLat = point.getLat();
        double initialLon = point.getLon();
        double initialDep = point.getDep();
        
        // Calculate initial residual
        double[] sWaveTravelTime = travelTime(this.stationTable, usedIdx, point);
        double[] travelTimeResidual = differentialTravelTimeResidual(lagTable, sWaveTravelTime);
        double initialRes = standardDeviation(travelTimeResidual);
        
        logger.info(String.format("Initial hypocenter: (%.6f, %.6f, %.3f), residual: %.6f",
            initialLat, initialLon, initialDep, initialRes));
        
        // Define search bounds
        double latRange = 1.0; // degree
        double lonRange = 1.0; // degree
        double depRange = 10.0; // km
        
        double latMin = initialLat - latRange;
        double latMax = initialLat + latRange;
        double lonMin = initialLon - lonRange;
        double lonMax = initialLon + lonRange;
        double depMin = Math.max(stnBottom, initialDep - depRange);
        double depMax = Math.min(hypBottom, initialDep + depRange);
        
        // Initialize population
        Individual[] population = initializePopulation(
            latMin, latMax, lonMin, lonMax, depMin, depMax, lagTable, usedIdx);
        
        // Find best individual in initial population
        Individual bestIndividual = findBestIndividual(population);
        logger.info(String.format("Initial best: (%.6f, %.6f, %.3f), residual: %.6f",
            bestIndividual.lat, bestIndividual.lon, bestIndividual.dep, bestIndividual.fitness));
        
        // Main DE loop
        Random random = new Random();
        int generation = 0;
        
        for (generation = 0; generation < maxGenerations; generation++) {
            // Check for interruption
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Differential evolution interrupted by user");
                throw new RuntimeException("Differential evolution was interrupted");
            }
            
            Individual[] newPopulation = new Individual[populationSize];
            
            for (int i = 0; i < populationSize; i++) {
                // Mutation: DE/rand/1
                int r1, r2, r3;
                do {
                    r1 = random.nextInt(populationSize);
                } while (r1 == i);
                do {
                    r2 = random.nextInt(populationSize);
                } while (r2 == i || r2 == r1);
                do {
                    r3 = random.nextInt(populationSize);
                } while (r3 == i || r3 == r1 || r3 == r2);
                
                Individual base = population[r1];
                Individual diff1 = population[r2];
                Individual diff2 = population[r3];
                
                // Mutant vector: v = x_r1 + F * (x_r2 - x_r3)
                double mutantLat = base.lat + scalingFactor * (diff1.lat - diff2.lat);
                double mutantLon = base.lon + scalingFactor * (diff1.lon - diff2.lon);
                double mutantDep = base.dep + scalingFactor * (diff1.dep - diff2.dep);
                
                // Apply bounds
                mutantLat = Math.max(latMin, Math.min(latMax, mutantLat));
                mutantLon = Math.max(lonMin, Math.min(lonMax, mutantLon));
                mutantDep = Math.max(depMin, Math.min(depMax, mutantDep));
                
                // Crossover: binomial crossover
                int jRand = random.nextInt(3); // Ensure at least one parameter is from mutant
                double trialLat = (random.nextDouble() < crossoverRate || jRand == 0) ? 
                    mutantLat : population[i].lat;
                double trialLon = (random.nextDouble() < crossoverRate || jRand == 1) ? 
                    mutantLon : population[i].lon;
                double trialDep = (random.nextDouble() < crossoverRate || jRand == 2) ? 
                    mutantDep : population[i].dep;
                
                // Evaluate trial individual
                double trialFitness = evaluateFitness(trialLat, trialLon, trialDep, lagTable, usedIdx);
                
                // Selection: greedy selection
                if (trialFitness < population[i].fitness) {
                    newPopulation[i] = new Individual(trialLat, trialLon, trialDep, trialFitness);
                } else {
                    newPopulation[i] = new Individual(population[i]);
                }
            }
            
            population = newPopulation;
            
            // Update best individual
            Individual currentBest = findBestIndividual(population);
            if (currentBest.fitness < bestIndividual.fitness) {
                bestIndividual = new Individual(currentBest);
            }
            
            // Report convergence
            if (convergenceCallback != null) {
                convergenceCallback.onResidualUpdate(generation, bestIndividual.fitness);
            }
            
            // Log progress every 10 generations
            if ((generation + 1) % 10 == 0 || generation == 0) {
                logger.info(String.format("Generation %d: best residual = %.6f, (%.6f, %.6f, %.3f)",
                    generation + 1, bestIndividual.fitness, 
                    bestIndividual.lat, bestIndividual.lon, bestIndividual.dep));
            }
        }
        
        logger.info(String.format("DE completed after %d generations. Best: (%.6f, %.6f, %.3f), residual: %.6f",
            maxGenerations, bestIndividual.lat, bestIndividual.lon, bestIndividual.dep, bestIndividual.fitness));
        
        // Estimate errors from final generation population distribution
        // Calculate standard deviation of individuals in the final population
        double meanLat = 0.0, meanLon = 0.0, meanDep = 0.0;
        for (Individual ind : population) {
            meanLat += ind.lat;
            meanLon += ind.lon;
            meanDep += ind.dep;
        }
        meanLat /= populationSize;
        meanLon /= populationSize;
        meanDep /= populationSize;
        
        double varLat = 0.0, varLon = 0.0, varDep = 0.0;
        for (Individual ind : population) {
            varLat += Math.pow(ind.lat - meanLat, 2);
            varLon += Math.pow(ind.lon - meanLon, 2);
            varDep += Math.pow(ind.dep - meanDep, 2);
        }
        double stdLat = Math.sqrt(varLat / populationSize);
        double stdLon = Math.sqrt(varLon / populationSize);
        double stdDep = Math.sqrt(varDep / populationSize);
        
        // Convert from degrees to km for latitude and longitude
        double latRad = Math.toRadians(bestIndividual.lat);
        double stdLatKm = stdLat * getDeg2Km();
        double stdLonKm = stdLon * getDeg2Km() * Math.cos(latRad);
        
        logger.info(String.format("Error estimates from final population: elat=%.3f km, elon=%.3f km, edep=%.3f km",
            stdLatKm, stdLonKm, stdDep));
        
        // Calculate final residual and weights
        Point finalPoint = new Point(time, bestIndividual.lat, bestIndividual.lon, bestIndividual.dep,
            0, 0, 0, 0, point.getFilePath(), "DE", point.getCid());
        double[] finalSWaveTravelTime = travelTime(this.stationTable, usedIdx, finalPoint);
        double[] finalTravelTimeResidual = differentialTravelTimeResidual(lagTable, finalSWaveTravelTime);
        double finalRes = standardDeviation(finalTravelTimeResidual);
        
        double[] weight = residual2weight(finalTravelTimeResidual);
        for (int i = 0; i < weight.length; i++) {
            lagTable[i][3] = weight[i];
        }
        
        // Set results
        point.setLat(bestIndividual.lat);
        point.setLon(bestIndividual.lon);
        point.setDep(bestIndividual.dep);
        point.setRes(finalRes);
        point.setElat(stdLatKm);
        point.setElon(stdLonKm);
        point.setEdep(stdDep);
        point.setResidual(finalTravelTimeResidual);
        point.setLagTable(lagTable);
        point.setType("DE");
        
        // Update pointsHandler
        pointsHandler.setMainPoint(point);
        
        try {
            pointsHandler.writeDatFile(outFile, codeStrings);
            logger.info("Differential Evolution location completed for: " + fileName);
            SolverLogger.fine("Differential Evolution location completed for: " + fileName);
        } catch (IOException e) {
            StringBuilder errorMsg = new StringBuilder("Failed to write output file in DE mode:\n");
            errorMsg.append("  Output file: ").append(outFile).append("\n");
            errorMsg.append("  Input file: ").append(datFile).append("\n");
            errorMsg.append("  Error: ").append(e.getMessage()).append("\n");
            if (e.getCause() != null) {
                errorMsg.append("  Caused by: ").append(e.getCause().getMessage()).append("\n");
            }
            logger.severe(errorMsg.toString());
            throw new RuntimeException("Failed to write output file: " + outFile, e);
        }
        
        logger.info(String.format("%s %.3f %.3f %.3f %.3f %.3f %.3f %.3f",
            time, bestIndividual.lon, bestIndividual.lat, bestIndividual.dep, 0.0, 0.0, 0.0, finalRes));
    }
    
    /**
     * Initializes the population with random individuals within the search bounds.
     * 
     * @param latMin minimum latitude
     * @param latMax maximum latitude
     * @param lonMin minimum longitude
     * @param lonMax maximum longitude
     * @param depMin minimum depth
     * @param depMax maximum depth
     * @param lagTable the lag table
     * @param usedIdx the indices of stations to use
     * @return array of initialized individuals
     */
    private Individual[] initializePopulation(double latMin, double latMax,
                                             double lonMin, double lonMax,
                                             double depMin, double depMax,
                                             double[][] lagTable, int[] usedIdx) {
        Individual[] population = new Individual[populationSize];
        Random random = new Random();
        
        for (int i = 0; i < populationSize; i++) {
            double lat = latMin + random.nextDouble() * (latMax - latMin);
            double lon = lonMin + random.nextDouble() * (lonMax - lonMin);
            double dep = depMin + random.nextDouble() * (depMax - depMin);
            
            double fitness = evaluateFitness(lat, lon, dep, lagTable, usedIdx);
            population[i] = new Individual(lat, lon, dep, fitness);
        }
        
        return population;
    }
    
    /**
     * Evaluates the fitness (residual) of a candidate hypocenter.
     * 
     * @param lat latitude in degrees
     * @param lon longitude in degrees
     * @param dep depth in km
     * @param lagTable the lag table
     * @param usedIdx the indices of stations to use
     * @return the fitness value (standard deviation of residuals)
     */
    private double evaluateFitness(double lat, double lon, double dep,
                                   double[][] lagTable, int[] usedIdx) {
        try {
            Point testPoint = new Point("", lat, lon, dep, 0, 0, 0, 0, "", "", -999);
            double[] sWaveTravelTime = travelTime(stationTable, usedIdx, testPoint);
            double[] residual = differentialTravelTimeResidual(lagTable, sWaveTravelTime);
            return standardDeviation(residual);
        } catch (TauModelException e) {
            logger.warning("Failed to calculate travel time: " + e.getMessage());
            return Double.MAX_VALUE; // Penalty for invalid solutions
        }
    }
    
    /**
     * Finds the best individual (lowest fitness) in the population.
     * 
     * @param population the population
     * @return the best individual
     */
    private Individual findBestIndividual(Individual[] population) {
        Individual best = population[0];
        for (int i = 1; i < population.length; i++) {
            if (population[i].fitness < best.fitness) {
                best = population[i];
            }
        }
        return best;
    }
    
    /**
     * Calculates the differential travel time residual between observed and calculated travel times.
     * 
     * @param lagTable the lag table containing the observed travel times
     * @param sWaveTravelTime the calculated travel times
     * @return an array containing the differential travel time residuals
     */
    private static double[] differentialTravelTimeResidual(double[][] lagTable, double[] sWaveTravelTime) {
        double[] diffTime = new double[lagTable.length];
        for (int i = 0; i < lagTable.length; i++) {
            double sTimeK = sWaveTravelTime[(int) lagTable[i][0]];
            double sTimeL = sWaveTravelTime[(int) lagTable[i][1]];
            double calcTime = sTimeL - sTimeK;
            diffTime[i] = lagTable[i][2] - calcTime; // O-C time
        }
        return diffTime;
    }
    
    /**
     * Calculates the standard deviation of an array of values.
     * 
     * @param data the array of values
     * @return the standard deviation
     */
    private static double standardDeviation(double[] data) {
        if (data.length == 0) return 0.0;
        
        double sum = 0.0;
        int length = data.length;
        
        for (double num : data) {
            sum += num;
        }
        double mean = sum / length;
        
        double variance = 0.0;
        for (double num : data) {
            variance += Math.pow(num - mean, 2);
        }
        return Math.sqrt(variance / length);
    }
}
