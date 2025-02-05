# xTreLoc

xTreLoc is a Java-based program for (re)determining the hypocenters of tremors based on cross-correlation.

## Requirements

- Java 17
- Supports both Maven and Gradle

## Installation

You can use either Maven or Gradle to build the project. Ensure you have Java 17 installed before proceeding.

### Using Maven

```sh
mvn clean install
```

### Using Gradle

```sh
gradle clean build
```

## Usage

Once built, you can execute the program using the following command:

```sh
java -jar /path/to/jarfile MODE
```

### Modes

xTreLoc supports multiple execution modes:

- **GRS**: Determines hypocenters using the Grid Search algorithm.
- **STD**: Determines hypocenters using the Station-Pair Double Difference method.
- **CLS**: Performs spatial clustering of hypocenters and generates `triplediff.csv` for TRP mode.
- **TRP**: Re-determines hypocenters using the Triple Difference method.
- **SYN**: Runs synthetic tests, referencing `catalog_file` in `config.json`.
- **SEE**: Plots the epicentral distribution, referencing `catalog_file` in `config.json`.

## Required Files

To run xTreLoc, the following files are required:

- **config.json**: Parameter settings file.
- **station.tbl**: A space-separated file containing station latitude, longitude, elevation, and station correction values.
- **Velocity Structure Model (TauP format)**: Optional. The default TauP models can be used if not specified.
- **catalog.csv**: A CSV file storing hypocenter information. It can also serve as the ground truth for synthetic tests.

## Sample Workflow

To use xTreLoc in a complete workflow, follow these steps:

1. Run **SYN** mode to generate `dat` files inside `dat-syn` (referenced by `dat_dir` in `config.json`).
2. Run **STD** mode to determine individual hypocenters.
3. Run **CLS** mode to perform spatial clustering and generate `triplediff.csv`.
4. Run **TRP** mode to re-determine hypocenters using the Triple Difference method.

All necessary files for this workflow are stored in the `test` directory.

## Additional Tools

Several auxiliary programs are included in the `test` directory:

- **sumdat.py** (Python script): 
    This script reads all files in `dat_dir` and generates a visualization script in the specified format (default: gnuplot).
  - Usage:
    ```sh
    python3 sumdat.py [-h] --dat_dir DAT_DIR [--map_type {gnuplot,python,matlab,gmt6}]
    ```

## License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.

## Version

- **Current Version:** 0.1

## Author

- **Developer:** A.B.
- **Contact:** [example@gmail.com](mailto:example@gmail.com)

## Repository

The source code will be available on GitHub soon.

