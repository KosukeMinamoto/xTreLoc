@import "style.css"

# xTreLoc User Manual (English)

## Table of Contents

<!-- @import "[TOC]" {cmd="toc" depthFrom=2 depthTo=3 orderedList=false} -->

<!-- code_chunk_output -->

- [Table of Contents](#table-of-contents)
- [Introduction](#introduction)
- [Installation and Build](#installation-and-build)
  - [Requirements](#requirements)
  - [Building from Source](#building-from-source)
  - [Verifying the Build](#verifying-the-build)
  - [Running JAR Files](#running-jar-files)
- [Data Formats](#data-formats)
  - [Station File Format (station.tbl)](#station-file-format-stationtbl)
  - [Catalog CSV Format](#catalog-csv-format)
  - [Travel Time Difference Data File Format](#travel-time-difference-data-file-format)
- [GUI Mode](#gui-mode)
- [CLI Mode](#cli-mode)
  - [Starting CLI Mode](#starting-cli-mode)
  - [Command Syntax](#command-syntax)
  - [Available Modes](#available-modes)
  - [Help](#help)
- [Demo Dataset Tutorial](#demo-dataset-tutorial)
  - [Preparation](#preparation)
  - [Demo Dataset Structure](#demo-dataset-structure)
  - [Workflow Overview](#workflow-overview)
  - [Step 1: Synthetic Data Generation](#step-1-synthetic-data-generation)
  - [Step 2: Hypocenter Location](#step-2-hypocenter-location)
  - [Step 3: Hypocenter Relocation](#step-3-hypocenter-relocation)
  - [Step 4: Result Visualization](#step-4-result-visualization)
  - [Output Results](#output-results)
- [Citation](#citation)

<!-- /code_chunk_output -->



---

## Introduction

xTreLoc is a hypocenter location software that supports multiple location methods:

- **GRD**: Returns a grid that minimizes travel time difference residuals using focused random search. Should be executed before STD mode.
- **STD**: Hypocenter location for individual events using the Station-pair Double Difference method. A Java port of the Fortran `hypoEcc` (Ide, 2010; Ohta et al., 2019) with minor bug fixes in `delaz4.f`, etc. Should be executed before TRD mode.
- **MCMC**: Hypocenter location using Markov Chain Monte Carlo method. Provides uncertainty estimation.
- **TRD**: Relative hypocenter relocation using the Triple Difference method by Guo & Zhang (2016).
- **CLS**: Constructs a network of hypocenters through spatial clustering and calculates differences in travel time differences between events. Equivalent to the role of `ph2dt` in `hypoDD` (Waldhauser & Ellsworth, 2000) and must be executed before TRD mode.
- **SYN**: Creates synthetic data that can be directly used in location modes (GRD, MCMC, STD & TRD).

The software can be used in two modes:

- **GUI Mode**: Interactive graphical user interface
- **CLI Mode**: Command-line interface for batch processing

---

## Installation and Build

### Requirements

- **Java 20 or higher** (check with `java -version`)
- Operating system: Windows, macOS, or Linux
- **Gradle** (included in `gradlew` wrapper, or install separately) or **Maven 3.6+**

### Building from Source

#### Using Gradle

```bash
# Clone the repository
git clone <repository-url>
cd xTreLoc

# Build GUI version
./gradlew build

# Build CLI version
./gradlew cliJar

# Build both
./gradlew build cliJar
```

Built JAR files are placed in `build/libs/`:
- `xTreLoc-GUI-1.0-SNAPSHOT.jar` (GUI version)
- `xTreLoc-CLI-1.0-SNAPSHOT.jar` (CLI version)

#### Using Maven

```bash
# Clone the repository
git clone <repository-url>
cd xTreLoc

# Build both GUI and CLI versions
mvn clean package
```

Built JAR files are placed in `target/`:
- `xTreLoc-GUI-1.0-SNAPSHOT.jar` (GUI version)
- `xTreLoc-CLI-1.0-SNAPSHOT.jar` (CLI version)

**Build options:**
- Build GUI version only: `mvn clean package -Pgui`
- Build CLI version only: `mvn clean package -Pcli`

### Verifying the Build

After building, verify that JAR files exist:

**Gradle:**
```bash
ls -lh build/libs/*.jar
```

**Maven:**
```bash
ls -lh target/*.jar
```

### Running JAR Files

**GUI Mode:**
```bash
# Using Gradle build
java -jar build/libs/xTreLoc-GUI-1.0-SNAPSHOT.jar

# Using Maven build
java -jar target/xTreLoc-GUI-1.0-SNAPSHOT.jar
```

**CLI Mode:**
```bash
# Using Gradle build
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar <MODE> config.json

# Using Maven build
java -jar target/xTreLoc-CLI-1.0-SNAPSHOT.jar <MODE> config.json
```

---

## Data Formats

### Station File Format (station.tbl)

Space-separated format:
```
station,latitude,longitude,depth,Pc,Sc
```

Example:
```
ST01 39.00 142.20 -1000 0.40 0.68
ST02 39.50 142.20 -1670 0.88 1.50
```

**Field Descriptions**:
- **Station name**: Station identifier
- **Latitude**: Latitude in decimal notation (positive for north)
- **Longitude**: Longitude in decimal notation (positive for east)
- **H**: Elevation in meters (positive downward, negative above sea level)
- **Sc**: Station correction value added to theoretical travel time difference for S-wave (seconds)
- **Pc**: Station correction value added to theoretical travel time difference for P-wave (seconds)

### Catalog CSV Format

CSV format with header:
```
time,latitude,longitude,depth,xerr,yerr,zerr,rms,file,mode,cid
```

Example:
```
2000-01-01T00:00:00,39.5000,142.4000,15.0000,0.0000,0.0000,0.0000,0.0000,dat/000101.000000.dat,SYN,0
```

**Field Descriptions**:
- **time**: Event time (ISO 8601 format, e.g., 2000-01-01T00:00:00)
- **latitude**: Latitude in decimal notation (positive for north)
- **longitude**: Longitude in decimal notation (positive for east)
- **depth**: Depth (km, positive downward)
- **xerr**: Location error in latitude direction (km)
- **yerr**: Location error in longitude direction (km)
- **zerr**: Location error in depth direction (km)
- **rms**: RMS travel time difference residual (seconds)
- **file**: Path to corresponding `.dat` file
- **mode**: Event type (SYN, GRD, STD, MCMC, TRD, ERR, REF)
- **cid**: Cluster ID (integer, 0 for events not classified into clusters)

**Event Types**:
- **SYN**: Events generated by SYN mode
- **GRD**: Events located by GRD mode
- **STD**: Events located by STD mode
- **TRD**: Events relocated by TRD mode
- **ERR**: Events that encountered errors during location (e.g., airquake)
- **REF**: Events that are only referenced in CLS and TRD modes and fixed as references

### Travel Time Difference Data File Format

Space-separated file managing hypocenter and travel time differences for each event.

**Format**:
- **Line 1**: `latitude longitude depth located_mode`
- **Line 2**: `latitude_error longitude_error depth_error RMS_travel_time_difference_residual`
- **Line 3 and onwards**: `station1 station2 travel_time_difference weight`
 
**Example**:
```
39.476 142.367 14.015 SYN
0.030 0.030 3.340 -999.000
ST07 ST09 -0.889 1.000
ST03 CA00 -12.975 1.000
ST02 ST09 12.280 1.000
```

**Field Descriptions**:
- **Travel time difference**: Travel time difference for S-wave. Corresponds to the value T_st2 - T_st1, where T_st1 and T_st2 are arrival times at stations st1 and st2, respectively.
- **Weight**: Data quality weight. Compared with `threshold` in `config.json` to determine whether to use the detection value. For example, by inputting the maximum value of the cross-correlation function, when `threshold: 0.6`, only detections with CC>0.6 are used for location.
In SYN mode, all values are set to 1.0. In GRD, STD, and MCMC modes, the absolute value of the inverse of the travel time difference residual is written. For example, a detection with a residual of -2 seconds has a weight of `0.5`, and the acceptable residual in the next location step (STD, MCMC, CLS, TRD) can be controlled with `threshold`. Note that in STD mode, since it solves an overdetermined problem, an error is output if there are fewer than 4 detections exceeding `threshold`.

---

## GUI Mode

The GUI consists of three main tabs:
1. Solver tab: Configuration and execution of hypocenter location calculations.
2. Viewer tab: Visualization of hypocenter positions and result analysis.
3. Settings tab: Configuration of application settings.

Launch with the following command:

```bash
java -jar build/libs/xTreLoc-GUI-1.0-SNAPSHOT.jar
```

Or using Gradle:

```bash
./gradlew run
```

---

## CLI Mode

### Starting CLI Mode

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar <MODE> [config.json]
```

Or using Gradle:

```bash
./gradlew runCLI -PcliArgs="<MODE> [config.json]"
```

### Command Syntax

```
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar <MODE> [config.json]
```

- `<MODE>`: One of GRD, STD, MCMC, TRD, CLS, or SYN
- `[config.json]`: Path to configuration file (Default: `config.json`)

### Available Modes

#### GRD Mode (Grid Search)

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar GRD config.json
```

Executes grid search hypocenter location using focused random search for all `.dat` files in the specified directory.

#### STD Mode

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar STD config.json
```

Applies the Station-pair Double Difference method to all `.dat` files.

#### MCMC Mode

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar MCMC config.json
```

Executes hypocenter location using MCMC for all `.dat` files.

#### CLS Mode (Spatial Clustering)

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar CLS config.json
```

Performs spatial clustering on a catalog file using the DBSCAN algorithm and creates triple difference data in binary format. Equivalent to the functionality of `ph2dt` in `hypoDD`.

#### TRD Mode

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar TRD config.json
```

Executes the Triple-Difference method on a catalog file using the method by Guo & Zhang (2016). Requires binary files from CLS mode.

#### SYN Mode (Synthetic Test Data Generation)

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar SYN config.json
```

Generates synthetic data in dat format from a ground truth catalog.

### Help

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar --help
```

Or

```bash
java -jar xTreLoc-CLI-1.0-SNAPSHOT.jar
```

---

## Demo Dataset Tutorial

This tutorial explains how to use xTreLoc using sample data in the `demo/` directory.

**Important**: All commands in this tutorial should be executed from the project root directory (the directory containing the `demo/` folder). The paths in `config.json` are relative to this directory. In other words, the current working directory should be the project root (one level above `demo/`) when executing commands.

### Preparation

- Java 20 or higher installed
- xTreLoc JAR files built (GUI and/or CLI)
- **Current directory**: Make sure you are in the project root directory (the directory containing `demo/`, `config.json`, etc.)
- Sample data in `demo/` directory:
  - `catalog_ground_truth.csv`: Ground truth catalog
  - `station.tbl`: Station file
- Gnuplot (optional, for visualization scripts)

- The following `config.json` is required (when running in CLI mode)
  ```json
  {
      "logLevel": "INFO",
      "numJobs": 4,
      "stationFile": "./demo/station.tbl",
      "taupFile": "prem",
      "hypBottom": 40.0,
      "threshold": 0.0,
      "modes": {
          "GRD": {
              "datDirectory": "./demo/dat",
              "outDirectory": "./demo/dat-grd"
          },
          "STD": {
              "datDirectory": "./demo/dat",
              "outDirectory": "./demo/dat-std"
          },
          "MCMC": {
              "datDirectory": "./demo/dat",
              "outDirectory": "./demo/dat-mcmc"
          },
          "TRD": {
              "catalogFile": "./demo/dat-cls/catalog.csv",
              "datDirectory": "./demo/dat-cls",
              "outDirectory": "./demo/dat-trd"
          },
          "CLS": {
              "catalogFile": "./demo/dat-cls/catalog.csv",
              "outDirectory": "./demo/dat-cls",
              "minPts": 5,
              "eps": 10.0
          },
          "SYN": {
              "catalogFile": "./demo/catalog_ground_truth.csv",
              "outDirectory": "./demo/dat",
              "randomSeed": 200,
              "phsErr": 0.15,
              "locErr": 0.05,
              "minSelectRate": 0.3,
              "maxSelectRate": 0.5
          }
      },
      "solver": {
          "GRD": {
              "totalGrids": 300,
              "numFocus": 3
          },
          "MCMC": {
              "nSamples": 1000,
              "burnIn": 200,
              "stepSize": 0.1,
              "temperature": 1.0
          },
          "TRD": {
              "iterNum": [10, 10],
              "distKm": [50, 20],
              "dampFact": [0, 1]
          }
      }
  }
  ```

### Demo Dataset Structure

```sh
mkdir -p ./demo/dat
```

```
demo/
├── catalog_ground_truth.csv  # Ground truth catalog for SYN mode
├── station.tbl                # Station file
└── dat/                       # Input .dat files (generated by SYN mode)
```

### Workflow Overview

```
Step 1: SYN → Generate .dat files
  ↓
Step 2: Execute location methods GRD+STD or MCMC → Output catalog.csv
  ↓
Step 3: Visualization → Gnuplot/Matlab/Python or GUI
  ↓
Step 4: CLS → Clustered catalog.csv, triple difference data (triple_diff_*.bin)
  ↓
Step 5: TRD
```

Each location mode has roughly the following characteristics:
- **GRD Mode**: Suitable for rough distribution estimation and initial hypocenter location
- **STD Mode**: Fast with good accuracy, but significantly affected by outliers in detection values, so it is recommended to execute after GRD mode.
- **MCMC Mode**: Moderate speed but good location accuracy.
- **TRD Mode**: However, for events with few detection data, some data may diverge, often resulting in reduced data. Also, the location accuracy of input data significantly affects results. Computationally intensive, so bootstrap-based error estimation is not currently implemented.

### Step 1: Synthetic Data Generation

**Purpose**: Create synthetic travel time difference data from a ground truth catalog.

**Using GUI**:
1. Launch the application: `java -jar build/libs/xTreLoc-GUI-1.0-SNAPSHOT.jar`
2. Navigate to the "solver" tab
3. Select mode: **SYN**
4. Settings:
   - **Catalog file**: `./demo/catalog_ground_truth.csv`
   - **Output directory**: `./demo/dat`
   - **Parameters**:
     - Random seed: Seed value (Default: 100)
     - Phase error (phsErr): Perturbation applied to travel time differences (seconds) (Default: 0.1)
     - Location error (locErr): Perturbation applied to hypocenter positions (deg) (Default: 0.03)
     - Minimum selection rate: <u>Minimum</u> number of travel time difference data to be selected (Default: 0.2)
     - Maximum selection rate: <u>Maximum</u> number of travel time difference data to be selected (Default: 0.4)
   Note that `phsErr` and `locErr` values correspond to the standard deviation of a Gaussian distribution. The maximum/minimum selection rates determine what proportion of travel time difference data to use from the possible station pairs (nC2, where n is the number of stations).
5. Click "▶ Execute"

**Using CLI**:

```bash
# Make sure you are in the project root directory (containing demo/ and config.json)
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar SYN config.json
```

- `phsErr`: Perturbation applied to travel time differences (Default: 0.1 seconds)
- `locErr`: Perturbation applied to hypocenter (Default: horizontal 0.03 degrees, depth 3 km)
- `minSelectRate, maxSelectRate`: Range of number of detections randomly selected (Default: 20-40%)

### Step 2: Hypocenter Location

#### Option A: GRD & STD

Determine hypocenter positions using STD method after focused random search.

**Using GUI**:
1. Select mode: **GRD**
1. Settings:
   - **Input directory**: `./demo/dat`
   - **Output directory**: `./demo/dat-grd` (must exist beforehand)
   - **Parameters**:
     - Parallelization (numJobs): Number of jobs in parallel computation (>1)
     - Weight Threshold (threshold): Weight value screening (0 means all travel time data are used)
     - Maximum Depth: Depth limit to be explored. On the other hand, the shallow limit is the depth of the deepest station.
     - Total Grids: Total number of grids to explore (Default: 300)
     - Focus Grids: Number of steps to narrow the region (Default: 3)
     For example, in the above example, 100 (=300/3) grids are randomly placed and explored in 3 rounds while narrowing the target range (i.e., scatter 100 grids in the region surrounding the station range, identify point $p_1$ with minimum residual. Next, scatter 100 points around $p_1$, identify the best point $p_2$. Similarly, select 100 points in a finer region around $p_2$ and take the best $p_3$ as the solution.)
1. Click "▶ Execute"
1. Select mode: **STD**
1. Settings:
   - **Input directory**: `./demo/dat-grd`
   - **Output directory**: `./demo/dat-std` (must exist beforehand)
   - **Parameters**:
     - Parallelization (numJobs): Number of jobs in parallel computation (>1)
     - Weight Threshold (threshold): Weight value screening (0 means all travel time data are used)
     - Maximum Depth: Depth limit to be explored. On the other hand, the shallow limit is the depth of the deepest station.
     - LM Initial Step Bound (initialStepBoundFactor): 100
     - LM Cost Relative Tolerance (costRelativeTolerance): 1e-6
     - LM Parameter Relative Tolerance (parRelativeTolerance): 1e-6
     - LM Orthogonal Tolerance (orthoTolerance): 1e-6
     - LM Max Evaluations (maxEvaluations): 1000
     - LM Max Iterations (maxIterations): 1000
     Changes to LM method parameters are generally not recommended.

1. Click "▶ Execute"

**Using CLI**:
```bash
# Make sure you are in the project root directory
mkdir -p demo/dat-grd
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar GRD config.json
mkdir -p demo/dat-std
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar STD config.json
```

#### Option B: MCMC

Determine hypocenter positions using Markov Chain Monte Carlo method.

**Using GUI**:
1. Select mode: **MCMC**
2. Settings:
   - **Input directory**: `./demo/dat`
   - **Output directory**: `./demo/dat-mcmc` (must exist beforehand)
   - **Parameters**:
     - Parallelization (numJobs): Number of jobs in parallel computation (>1)
     - Weight Threshold (threshold): Weight value screening (0 means all travel time data are used)
     - Maximum Depth: Depth limit to be explored. On the other hand, the shallow limit is the depth of the deepest station.
     - Sample Count: Number of samples (Default: 1000)
     - Burn-in Period: Burn-in (Default: 200)
     - Step Size: Step size (Default: 0.1)
     - Depth Step Size: Step size in depth direction (Default: 1.0)
     - Temperature Parameter: (Default: 1.0)
3. Click "▶ Execute"

**Using CLI**:

```bash
# Make sure you are in the project root directory
mkdir -p ./demo/dat-mcmc
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar MCMC config.json
```

### Step 3: Hypocenter Relocation

CLS mode clustering and calculation of travel time difference differences between hypocenter pairs. In this example, files under `dat` generated by SYN mode are used for accuracy verification, but it is strongly recommended to input hypocenters determined by GRD & STD or MCMC from Step 2 in general.

**Using GUI**:
1. Select mode: **CLS**
2. Settings:
   - **Catalog file**: `./demo/dat/catalog.csv` (or `dat-std`, `dat-mcmc`)
   - **Output directory**: `./demo/dat-cls` (must exist beforehand)
   - **Parameters**:
     - Minimum points: Minimum number of points per cluster (3)
     - Epsilon (km): Cluster radius (30.0)
     - Weight Threshold (threshold): Weight value screening
     - Data Inclusion Rate (optional): When epsilon value is negative, automatic estimation using K-distance graph is performed. In this case, input what proportion of events to include relative to the total number of events when setting epsilon. If not set, automatic estimation is performed using the elbow method.
     - RMS Threshold (optional): Threshold for travel time difference in dat files (seconds). Events with travel time residuals exceeding this value are not used for clustering.
     - Location Error Threshold (optional): Threshold for horizontal hypocenter location accuracy in dat files (km). Events with errors exceeding this value are not used for clustering.

3. Click "▶ Execute"

**K-Distance Graph** (for epsilon estimation):
![K-Distance Graph](kdist.png)

**Using CLI**:
```bash
# Make sure you are in the project root directory
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar CLS config.json
```

**Note**: Cluster IDs start from 1 as consecutive numbers, and 0 corresponds to events not classified into clusters. If integer values are written in the 11th column (cid) of the catalog file, those cluster IDs are used (only triple difference data calculation is executed, so this is used when clustering is performed beforehand using a different method). If cluster numbers are not assigned and the corresponding column is blank, DBSCAN clustering is executed with `minPts` and `eps` as parameters. However, if `eps` is negative, clustering is performed with `eps` automatically estimated by k-distance graph (and elbow method if Data Inclusion Rate is not specified).
CLS mode outputs files corresponding to `dt.ct` in `hypoDD`, which are `triple_diff_<cid>.bin` (binary format). These are used for relative hypocenter location.

**Using GUI**:
1. Select mode: **TRD**
2. Settings:
   - **Catalog file**: `./demo/dat-cls/catalog.csv` (or other catalog)
   - **Input directory**: `./demo/dat-cls` (directory containing triple difference binary files)
   - **Output directory**: `./demo/dat-trd` (must exist beforehand)
   - **Parameters**:
     - Parallelization (numJobs): Number of jobs in parallel computation (>1)
     - Weight Threshold (threshold): Weight value screening (0 means all travel time data are used)
     - Maximum Depth: Depth limit to be explored. On the other hand, the shallow limit is the depth of the deepest station. <u>Unlike GRD, STD, and MCMC modes, if updated beyond this depth (or shallower than stations), it is judged as a location failure and an Err tag is assigned.</u>
     - Iteration Count (iterNum): Number of iterations in each step (e.g., 10,10)
     - Distance Threshold (distKm): Distance limit between events in each step (e.g., 50,20)
     - Damping Factor (dampFact): Damping coefficient in each step (e.g., 0,1)
     Iteration Count, Distance Threshold, and Damping Factor must be given as lists. In the above example, in the first step, a network is constructed with events within 50 km, and 10 iterations are performed with Damping Factor of 0. In the next step, similarly, 10 iterations are performed with Damping Factor=1 for events within 20 km. Therefore, the number of elements in these lists must be the same.
     In the catalog file, hypocenters of events labeled REF are not updated and are fixed.

3. Click "▶ Execute"

**Using CLI**:
```bash
# Make sure you are in the project root directory
mkdir -p demo/dat-trd
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar TRD config.json
```

### Step 4: Result Visualization

#### Gnuplot

Simple visualization of catalog results is possible using the provided Gnuplot script `map_njt.plt`.

**Usage**:
1. Navigate to the `demo/` directory:
   ```bash
   cd demo
   ```
2. Edit `map_njt.plt` and set the `filename_csv` variable to the catalog file:
   ```gnuplot
   filename_csv = "dat-std/catalog.csv"
   ```
3. Run gnuplot:
   ```bash
   gnuplot map_njt.plt
   ```
4. PDF is automatically generated.

**Example Outputs**:
- Ground truth: ![Ground Truth](gnu_ground_truth.png)

- Perturbed hypocenters: ![Perturbated](gnu_syn.png)

- GRD results: ![GRD Results](gnu_syn_grd.png)

- GRD&STD results: ![GRD&STD Results](gnu_syn_grd_std.png)

- MCMC results: ![MCMC Results](gnu_syn_mcmc.png)

- TRD results: ![TRD Results](gnu_syn_trd.png)

#### MATLAB

Maps can also be created using the provided MATLAB script `map_njt.m`.

**Usage**:
1. Edit `map_njt.m` and set the catalog file:
   ```matlab
   catalog_file = "demo/dat-std/catalog.csv";
   ```
1. Run MATLAB:
   ```matlab
   map_njt
   ```

**Example Output**: ![MATLAB Map](mat_njt.png)

#### Drawing on GUI

Navigate to the "viewer" tab, and in the "Catalog data" tab, click "File selection" to load a catalog file for drawing. By selecting shp files, etc., coastlines and trenches can also be drawn.

**GUI Visualization Example**:
![GUI Map View](gui_njt.png)

### Output Results

After completing all steps, the following is obtained:

```
demo
├── catalog_ground_truth.csv
├── dat
│   ├── 000101.000000.dat
│   ├── 000101.010000.dat
│   ├── ...
│   └── catalog.csv
├── dat-cls
│   ├── 000101.000000.dat
│   ├── 000101.010000.dat
│   ├── ...
│   ├── catalog.csv
│   ├── triple_diff_1.bin
│   └── triple_diff_2.bin
├── dat-grd
│   ├── 000101.000000.dat
│   ├── 000101.010000.dat
│   ├── ...
│   └── catalog.csv
├── dat-mcmc
│   ├── 000101.000000.dat
│   ├── 000101.010000.dat
│   ├── ...
│   └── catalog.csv
├── dat-std
│   ├── 000101.000000.dat
│   ├── 000101.010000.dat
│   ├── ...
│   └── catalog.csv
├── dat-trd
│   ├── 000101.000000.dat
│   ├── 000101.010000.dat
│   ├── ...
│   └── catalog_trd.csv
├── map_njt.m
├── map_njt.plt
└── station.tbl
```

## Citation

1. Natural Earth: https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-coastline/
1. The Geospatial Information Authority of Japan (GSI): https://www.gsi.go.jp/kankyochiri/gm_japan_e.html

---

**Version**: 1.0-SNAPSHOT  
**Last Updated**: 2024

