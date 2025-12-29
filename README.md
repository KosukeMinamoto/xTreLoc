# xTreLoc

Multi-method hypocenter location software with GUI and CLI support for earthquake location and relative relocation.

## Features

- **Multiple Location Methods**:
  - **GRD**: Grid search using focused random search
  - **STD**: Station-pair Double Difference method (Java port of `hypoEcc`)
  - **MCMC**: Markov Chain Monte Carlo method with uncertainty estimation
  - **TRD**: Triple Difference relative relocation (Guo & Zhang, 2016)
  - **CLS**: Spatial clustering and triple difference calculation (equivalent to `ph2dt` in `hypoDD`)
  - **SYN**: Synthetic data generation for testing

- **Dual Interface**: Interactive GUI and command-line interface for batch processing
- **Visualization**: Built-in tools for result analysis and mapping
- **Cross-platform**: Windows, macOS, and Linux support

## Quick Start

### Requirements

- Java 20 or higher
- Gradle (included via `gradlew` wrapper) or Maven 3.6+

### Build

#### Using Gradle
```bash
git clone https://github.com/KosukeMinamoto/xTreLoc.git
cd xTreLoc

# Build both GUI and CLI versions
./gradlew build cliJar
```

Built JAR files are in `build/libs/`:
- `xTreLoc-GUI-1.0-SNAPSHOT.jar` (GUI version)
- `xTreLoc-CLI-1.0-SNAPSHOT.jar` (CLI version)

#### Using Maven

```bash
git clone https://github.com/KosukeMinamoto/xTreLoc.git
cd xTreLoc

# Build both GUI and CLI versions
mvn clean package
```

Built JAR files are in `target/`:
- `xTreLoc-GUI-1.0-SNAPSHOT.jar` (GUI version)
- `xTreLoc-CLI-1.0-SNAPSHOT.jar` (CLI version)

**Build options:**
- Build GUI version only: `mvn clean package -Pgui`
- Build CLI version only: `mvn clean package -Pcli`
### Run

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

Available modes: `GRD`, `STD`, `MCMC`, `TRD`, `CLS`, `SYN`

## Creating Native Applications

### macOS (.app Bundle)

You can create a native macOS application (`.app` bundle).

**Prerequisites:**
- JDK 14 or higher (includes jpackage tool)
- Run on macOS

**Steps:**

1. **Build JAR files:**
```bash
./gradlew jar
```

2. **Create .app bundle:**
```bash
./gradlew createApp
```

The created application will be located at `build/dist/xTreLoc.app`.
You can launch this application directly from Finder.

3. **Create DMG installer (optional):**
```bash
./gradlew createDmg
```

The DMG file will be created at `build/dist/xTreLoc-1.0-SNAPSHOT.dmg`.
By distributing this DMG file, users can easily install the application.

**Notes:**
- On first launch, macOS security settings may display a warning
- To avoid the warning, you need to add code signing to the application
- Code signing requires an Apple Developer account

### Windows/Linux

You can also create native applications for Windows and Linux using jpackage.
For details, see the [jpackage official documentation](https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html).

## Example Workflow

```bash
# 1. Generate synthetic data
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar SYN config.json
# or with Maven: java -jar target/xTreLoc-CLI-1.0-SNAPSHOT.jar SYN config.json

# 2. Locate hypocenters (GRD + STD)
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar GRD config.json
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar STD config.json

# 3. Cluster events and calculate triple differences
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar CLS config.json

# 4. Relative relocation
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar TRD config.json
```

## Documentation

- [User Manual (English)](docs/Turtorial.md)
- [User Manual (Japanese)](docs/TurtorialJP.md)

## Demo

Try the demo dataset in the `demo/` directory. See the [tutorial](docs/Turtorial.md#demo-dataset-tutorial) for detailed instructions.

## Citation

1. Natural Earth: https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-coastline/
2. The Geospatial Information Authority of Japan (GSI): https://www.gsi.go.jp/kankyochiri/gm_japan_e.html

## License

See [LICENSE](LICENSE) file for details.
