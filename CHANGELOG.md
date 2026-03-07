# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0-alpha] - 2026-03-08

### Added
- Initial release of xTreLoc (ver. alpha)
- Multiple location methods: GRD, LMO, MCMC, DE, TRD, CLS, SYN
- GUI, CLI, and TUI interfaces
- Built-in visualization (map, histograms, scatter, residual convergence plots)
- Waveform picking (SAC, NONLINLOC .obs export)
- Automatic update checking
- Cross-platform support (Windows, macOS, Linux)
- Native app packaging (jpackage): createApp / createNativeApp

### Features
- Grid search using focused random search (GRD)
- Levenberg-Marquardt optimization (LMO)
- Markov Chain Monte Carlo with uncertainty estimation (MCMC)
- Differential Evolution (DE)
- Triple Difference relative relocation (TRD)
- Spatial clustering and triple difference calculation (CLS)
- Synthetic data generation (SYN)

### Technical
- Java 20 required
- GeoTools 31.0, JFreeChart, FlatLaf for GUI
- TauP 2.6.1, GeographicLib, Jackson, Commons Math 3


