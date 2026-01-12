# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2025-01-12

### Added
- Initial release of xTreLoc
- Multiple location methods: GRD, LMO, MCMC, TRD, CLS, SYN
- GUI and CLI interfaces
- Built-in visualization tools
- Automatic update checking
- Cross-platform support (Windows, macOS, Linux)

### Features
- Grid search using focused random search (GRD)
- Levenberg-Marquardt optimization (LMO)
- Markov Chain Monte Carlo method with uncertainty estimation (MCMC)
- Triple Difference relative relocation (TRD)
- Spatial clustering and triple difference calculation (CLS)
- Synthetic data generation for testing (SYN)

### Technical Details
- Java 20 required
- GeoTools 31.0 for map visualization
- TauP 2.6.1 for travel time calculations
- GeographicLib for geodetic calculations

