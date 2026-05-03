# xTreLoc

xTreLoc is hypocenter location software that **determines earthquake hypocenters by optimizing differential travel times obtained from cross-correlation**. It supports GUI, CLI, and TUI, and offers multiple methods from single-event location to relative relocation.

## Features

- **Multiple Location Methods**
  - **GRD**: Grid search using focused random search (run before LMO).
  - **LMO**: Levenberg-Marquardt optimization (Java port of `hypoEcc`). Run before TRD.
  - **MCMC**: Markov Chain Monte Carlo with uncertainty estimation.
  - **DE**: Differential evolution; global optimization per event (alternative to LMO).
  - **TRD**: Triple Difference relative relocation (Guo & Zhang, 2016).
  - **CLS**: Spatial clustering and triple-difference data (equivalent to `ph2dt` in `hypoDD`). Run before TRD.
  - **SYN**: Synthetic data generation for testing.

- **Three Interfaces**
  - **GUI**: Interactive graphical interface (map, histograms, scatter, residual plots, report export).
  - **CLI**: Command-line interface for batch processing.
  - **TUI**: Text UI in the terminal (Lanterna). Menu-driven mode and parameter selection; suitable for SSH or lightweight runs.

- **Visualization**
  - Interactive map (catalog, stations, shapefiles).
  - Histograms and scatter plots; event screening.
  - Real-time residual convergence plots (LMO, MCMC, DE, TRD).
  - Report and catalog export.

- **Waveform Picking**: SAC support; P/S arrival picking and NONLINLOC `.obs` export.

- **Cross-platform**: Windows, macOS, Linux.

## Quick Start

### Requirements

- **Java 20 or higher** (`java -version`)
- **Gradle** (included via `gradlew`) or **Maven 3.6+**

### Build

#### Gradle

```bash
git clone https://github.com/KosukeMinamoto/xTreLoc.git
cd xTreLoc

# Main JAR (launcher: choose GUI / TUI / CLI on startup)
./gradlew build

# Optional: mode-specific JARs
./gradlew cliJar    # CLI only
./gradlew tuiJar    # TUI only
./gradlew guiJar    # GUI only
```

JARs in `build/libs/`:

| JAR | Command |
|-----|---------|
| `xTreLoc-1.0-SNAPSHOT.jar` | `./gradlew build` (launcher) |
| `xTreLoc-CLI-1.0-SNAPSHOT.jar` | `./gradlew cliJar` |
| `xTreLoc-TUI-1.0-SNAPSHOT.jar` | `./gradlew tuiJar` |
| `xTreLoc-GUI-1.0-SNAPSHOT.jar` | `./gradlew guiJar` |

#### Maven

```bash
git clone https://github.com/KosukeMinamoto/xTreLoc.git
cd xTreLoc
mvn clean package
```

JARs in `target/` (same layout as Gradle `jar`, `guiJar`, `cliJar`, `tuiJar`):

| JAR | Role |
|-----|------|
| `xTreLoc-1.0-SNAPSHOT.jar` | Launcher (`com.treloc.xtreloc.app.XTreLoc`) — full classpath |
| `xTreLoc-GUI-1.0-SNAPSHOT.jar` | GUI only |
| `xTreLoc-CLI-1.0-SNAPSHOT.jar` | CLI — GeoTools / FlatLaf / JFreeChart / JTS and `app/gui` excluded |
| `xTreLoc-TUI-1.0-SNAPSHOT.jar` | TUI — same slimming as CLI |

`mvn clean package` builds all four. An `original-*.jar` in `target/` may appear as a Shade backup; it is not needed to run the app.

#### Platform-specific build (Linux / Windows / macOS)

JARs are built the same way on all platforms. To create **native app-images or installers**, run the following **on the target OS** (JDK 20+ and `./gradlew build` required):

| OS | Gradle task | Description |
|----|--------------|-------------|
| **Linux** | `./gradlew createLinuxApp` | Creates app-image in `build/dist/`. Follow with `createLinuxTarball` for a `.tar.gz`. |
| **Windows** | `./gradlew createWindowsApp` | Creates app-image. Use `createWindowsExe` for an .exe installer (requires WiX 3+). |
| **macOS** | `./gradlew createApp` | Creates .app bundle in `build/dist/`. Use `createDmg` for a .dmg installer. |

On Windows use `gradlew.bat` instead of `./gradlew` (e.g. `gradlew.bat createWindowsApp`).

With Maven, build JARs on the target OS with `mvn clean package -Plinux`, `-Pwindows`, or `-Pmacos`; use the Gradle tasks above or run `jpackage` manually for native bundles.

### Run

**Launcher (choose GUI / TUI / CLI):**
```bash
java -jar build/libs/xTreLoc-1.0-SNAPSHOT.jar
# Maven build: java -jar target/xTreLoc-1.0-SNAPSHOT.jar
```

**GUI:**
```bash
java -jar build/libs/xTreLoc-GUI-1.0-SNAPSHOT.jar
# or: java -jar target/xTreLoc-GUI-1.0-SNAPSHOT.jar
```

**TUI:**
```bash
java -jar build/libs/xTreLoc-TUI-1.0-SNAPSHOT.jar [config.json]
# Maven: java -jar target/xTreLoc-TUI-1.0-SNAPSHOT.jar [config.json]
```

**CLI:**
```bash
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar <MODE> [config.json]
# or: java -jar target/xTreLoc-CLI-1.0-SNAPSHOT.jar <MODE> [config.json]
```
Default config path is `config.json` when omitted.

Modes: `GRD`, `LMO`, `MCMC`, `DE`, `TRD`, `CLS`, `SYN`.

**Configuration file**: The implementation uses top-level `io` (mode-specific input/output) and `params` (mode-specific parameters) in `config.json`. See `demo/locating_example/config.json` for a sample.

## Example Workflow

```bash
# 1. Synthetic data
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar SYN config.json

# 2. Location (GRD + LMO, or MCMC, or DE)
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar GRD config.json
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar LMO config.json

# 3. Clustering and triple-difference data
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar CLS config.json

# 4. Relative relocation
java -jar build/libs/xTreLoc-CLI-1.0-SNAPSHOT.jar TRD config.json
```

### Windows

Use `jpackage`; see [jpackage documentation](https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html).

## Documentation

- [User Manual (English)](docs/Turtorial.md)
- [User Manual (Japanese)](docs/TurtorialJP.md)
- [Release Checklist](scripts/RELEASE_CHECKLIST.md)

## Demo

Demo data: `demo/locating_example/`. See the [Demo Dataset Tutorial](docs/Turtorial.md#demo-dataset-tutorial) for steps.

## Citation

1. Natural Earth: <https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-coastline/>
2. GSI Japan: <https://www.gsi.go.jp/kankyochiri/gm_japan_e.html>

## License

See [LICENSE](LICENSE).
