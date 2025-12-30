# Technical Description of Hypocenter Location Methods

This document provides technical descriptions with mathematical formulations for each hypocenter location mode implemented in xTreLoc. The methods are based on established algorithms in seismology, with implementations adapted for efficient processing of travel time difference data.

## 1. GRD Mode: Grid Search with Focused Random Search

### Overview
The GRD mode performs hypocenter location using a grid search algorithm with focused random search. This method systematically explores a parameter space by evaluating multiple candidate locations.

### Mathematical Formulation

The objective function to minimize is the standard deviation of travel time residuals:

\[
\sigma = \sqrt{\frac{1}{N} \sum_{i=1}^{N} (r_i - \bar{r})^2}
\]

where \(r_i\) is the residual for the \(i\)-th station pair:

\[
r_i = \Delta t_{i}^{\text{obs}} - \Delta t_{i}^{\text{calc}}
\]

Here, \(\Delta t_{i}^{\text{obs}}\) is the observed differential travel time between stations \(k\) and \(l\) for the \(i\)-th pair:

\[
\Delta t_{i}^{\text{obs}} = t_{i,l} - t_{i,k}
\]

and \(\Delta t_{i}^{\text{calc}}\) is the calculated differential travel time:

\[
\Delta t_{i}^{\text{calc}} = T(\mathbf{x}, \mathbf{s}_l) - T(\mathbf{x}, \mathbf{s}_k)
\]

where:
- \(\mathbf{x} = (x, y, z)\) is the hypocenter location (longitude, latitude, depth)
- \(\mathbf{s}_k\) and \(\mathbf{s}_l\) are station positions
- \(T(\mathbf{x}, \mathbf{s})\) is the travel time from hypocenter \(\mathbf{x}\) to station \(\mathbf{s}\)

### Algorithm

1. **Initial Search**: Generate random grid points within initial search ranges:
   - Latitude: \([lat - 1°, lat + 1°]\)
   - Longitude: \([lon - 1°, lon + 1°]\)
   - Depth: \([\max(stnBottom, dep - 10 \text{ km}), \min(hypBottom, dep + 10 \text{ km})]\)

2. **Focused Search**: For each focus level \(f = 0, 1, \ldots, n_{\text{focus}} - 1\):
   - Search range factor: \(\alpha_f = 0.5^f\)
   - Generate \(n_{\text{grid}} / n_{\text{focus}}\) random points within reduced ranges
   - Update best location if residual improves

3. **Weight Calculation**: After finding the best location, calculate weights:
   \[
   w_i = \frac{1}{|\Delta t_{i}^{\text{obs}} - \Delta t_{i}^{\text{calc}}|}
   \]

### Parameters
- `totalGrids`: Total number of grid points to evaluate (default: 300)
- `numFocus`: Number of focus levels (default: 3)

---

## 2. STD Mode: Station-pair Double Difference Method

### Overview
The STD mode implements the Station-pair Double Difference method using the Levenberg-Marquardt (LM) optimization algorithm. This method minimizes the weighted sum of squared residuals for differential travel times between station pairs. The implementation is based on the method described by Ide (2010) and Ohta et al. (2019), which is a Java port of the Fortran `hypoEcc` program with improvements.

### Mathematical Formulation

The objective function is:

\[
\chi^2 = \sum_{i=1}^{N} w_i \left( \Delta t_{i}^{\text{obs}} - \Delta t_{i}^{\text{calc}}(\mathbf{x}) \right)^2
\]

where \(w_i\) is the weight for the \(i\)-th station pair.

The residual vector is:

\[
\mathbf{r}(\mathbf{x}) = \begin{pmatrix}
\Delta t_{1}^{\text{obs}} - \Delta t_{1}^{\text{calc}}(\mathbf{x}) \\
\Delta t_{2}^{\text{obs}} - \Delta t_{2}^{\text{calc}}(\mathbf{x}) \\
\vdots \\
\Delta t_{N}^{\text{obs}} - \Delta t_{N}^{\text{calc}}(\mathbf{x})
\end{pmatrix}
\]

The Jacobian matrix \(\mathbf{J}\) has elements:

\[
J_{ij} = \frac{\partial \Delta t_{i}^{\text{calc}}}{\partial x_j}
\]

where \(x_j\) represents longitude (\(j=0\)), latitude (\(j=1\)), or depth (\(j=2\)).

For station pair \((k, l)\):

\[
\frac{\partial \Delta t_{i}^{\text{calc}}}{\partial x_j} = \frac{\partial T(\mathbf{x}, \mathbf{s}_l)}{\partial x_j} - \frac{\partial T(\mathbf{x}, \mathbf{s}_k)}{\partial x_j}
\]

### Levenberg-Marquardt Algorithm

The LM algorithm solves the normal equations:

\[
(\mathbf{J}^T \mathbf{W} \mathbf{J} + \lambda \mathbf{I}) \Delta \mathbf{x} = \mathbf{J}^T \mathbf{W} \mathbf{r}
\]

where:
- \(\mathbf{W}\) is the diagonal weight matrix
- \(\lambda\) is the damping parameter (adjusted adaptively)
- \(\Delta \mathbf{x}\) is the parameter update

### Outlier Removal

Outliers are identified and removed iteratively:

\[
|r_i| > 2\sigma
\]

where \(\sigma\) is the RMS residual:

\[
\sigma = \sqrt{\frac{1}{N} \sum_{i=1}^{N} r_i^2}
\]

### Error Estimation

The covariance matrix is:

\[
\mathbf{C} = \sigma^2 (\mathbf{J}^T \mathbf{J})^{-1}
\]

Standard errors are:

\[
\sigma_{x_j} = \sqrt{C_{jj}}
\]

### Relationship to Envelope Correlation Method

The STD mode is related to the envelope correlation method used by Ide (2010) for tremor location. In the envelope correlation method, the travel time difference between station pairs is determined from the cross-correlation of envelope functions:

\[
\Delta t_{kl} = \arg\max_{\tau} \text{CC}(E_k(t), E_l(t + \tau))
\]

where \(E_k(t)\) and \(E_l(t)\) are envelope functions at stations \(k\) and \(l\), and CC denotes cross-correlation. The STD mode uses these travel time differences directly in the optimization, making it suitable for both traditional phase picking and envelope-based methods.

### Parameters
- `initialStepBoundFactor`: Initial step bound factor (default: 100.0)
- `costRelativeTolerance`: Cost relative tolerance (default: 1e-6)
- `parRelativeTolerance`: Parameter relative tolerance (default: 1e-6)
- `orthoTolerance`: Orthogonality tolerance (default: 1e-6)
- `maxEvaluations`: Maximum function evaluations (default: 1000)
- `maxIterations`: Maximum iterations (default: 1000)

---

## 3. MCMC Mode: Markov Chain Monte Carlo Method

### Overview
The MCMC mode uses the Metropolis-Hastings algorithm to sample the posterior distribution of hypocenter locations, providing uncertainty estimates.

### Mathematical Formulation

The posterior probability is:

\[
P(\mathbf{x} | \mathbf{d}) \propto P(\mathbf{d} | \mathbf{x}) P(\mathbf{x})
\]

where:
- \(P(\mathbf{d} | \mathbf{x})\) is the likelihood
- \(P(\mathbf{x})\) is the prior (assumed uniform within bounds)

The log-likelihood is:

\[
\log L(\mathbf{x}) = -\frac{1}{2} \sum_{i=1}^{N} \left( \frac{\Delta t_{i}^{\text{obs}} - \Delta t_{i}^{\text{calc}}(\mathbf{x})}{\sigma_i} \right)^2
\]

For simplicity, assuming equal uncertainties:

\[
\log L(\mathbf{x}) = -\sum_{i=1}^{N} \left( \Delta t_{i}^{\text{obs}} - \Delta t_{i}^{\text{calc}}(\mathbf{x}) \right)^2
\]

### Metropolis-Hastings Algorithm

1. **Proposal**: Generate candidate location:
   \[
   \mathbf{x}' = \mathbf{x}^{(t)} + \boldsymbol{\epsilon}
   \]
   where \(\boldsymbol{\epsilon} \sim \mathcal{N}(\mathbf{0}, \boldsymbol{\Sigma}_{\text{step}})\)

2. **Acceptance Probability**:
   \[
   \alpha = \min\left(1, \exp\left(\frac{\log L(\mathbf{x}') - \log L(\mathbf{x}^{(t)})}{T}\right)\right)
   \]
   where \(T\) is the temperature parameter.

3. **Accept/Reject**:
   - Accept \(\mathbf{x}'\) with probability \(\alpha\)
   - Otherwise, keep \(\mathbf{x}^{(t)}\)

### Statistics from Samples

After burn-in, calculate:

- **Mean**:
  \[
  \bar{x}_j = \frac{1}{N_{\text{samples}}} \sum_{t=1}^{N_{\text{samples}}} x_j^{(t)}
  \]

- **Standard Deviation**:
  \[
  \sigma_{x_j} = \sqrt{\frac{1}{N_{\text{samples}}} \sum_{t=1}^{N_{\text{samples}}} (x_j^{(t)} - \bar{x}_j)^2}
  \]

### Parameters
- `nSamples`: Total number of MCMC samples (default: 1000)
- `burnIn`: Number of burn-in samples (default: 200)
- `stepSize`: Step size for latitude/longitude in degrees (default: 0.1)
- `stepSizeDepth`: Step size for depth in km (default: 1.0)
- `temperature`: Temperature parameter (default: 1.0)

---

## 4. TRD Mode: Triple Difference Relocation

### Overview
The TRD mode implements the Triple Difference method (Guo & Zhang, 2016) for relative relocation of clustered events. This method minimizes differences in travel time differences between event pairs observed at station pairs. The triple difference approach is an extension of the double-difference method (Waldhauser & Ellsworth, 2000) and provides improved relative location accuracy by eliminating common path effects.

### Mathematical Formulation

The triple difference is defined as:

\[
\Delta \Delta t_{ij,kl} = \Delta t_{i,kl} - \Delta t_{j,kl}
\]

where:
- \(\Delta t_{i,kl} = t_{i,l} - t_{i,k}\) is the differential travel time for event \(i\) at station pair \((k, l)\)
- \(\Delta t_{j,kl} = t_{j,l} - t_{j,k}\) is the differential travel time for event \(j\) at station pair \((k, l)\)

The observed triple difference is:

\[
\Delta \Delta t_{ij,kl}^{\text{obs}} = \Delta t_{i,kl}^{\text{obs}} - \Delta t_{j,kl}^{\text{obs}}
\]

The calculated triple difference is:

\[
\Delta \Delta t_{ij,kl}^{\text{calc}} = \Delta t_{i,kl}^{\text{calc}}(\mathbf{x}_i) - \Delta t_{j,kl}^{\text{calc}}(\mathbf{x}_j)
\]

The residual is:

\[
d_{ij,kl} = \Delta \Delta t_{ij,kl}^{\text{obs}} - \Delta \Delta t_{ij,kl}^{\text{calc}}
\]

### Linearized Problem

For small perturbations \(\delta \mathbf{x}_i = (\delta x_i, \delta y_i, \delta z_i)\):

\[
d_{ij,kl} = \sum_{m=1}^{3} G_{ij,kl,m} \delta x_{i,m} - \sum_{m=1}^{3} G_{ij,kl,m} \delta x_{j,m}
\]

where the design matrix elements are:

\[
G_{ij,kl,m} = \frac{\partial \Delta t_{i,kl}^{\text{calc}}}{\partial x_{i,m}} = \frac{\partial T(\mathbf{x}_i, \mathbf{s}_l)}{\partial x_{i,m}} - \frac{\partial T(\mathbf{x}_i, \mathbf{s}_k)}{\partial x_{i,m}}
\]

In matrix form:

\[
\mathbf{d} = \mathbf{G} \delta \mathbf{x}
\]

where:
- \(\mathbf{d}\) is the residual vector (length \(M\), number of triple differences)
- \(\mathbf{G}\) is the design matrix (\(M \times 3N\), where \(N\) is number of target events)
- \(\delta \mathbf{x}\) is the parameter vector (length \(3N\))

### LSQR Solution

The system is solved using the LSQR algorithm (Paige & Saunders, 1982) with damping:

\[
\min \left\| \begin{pmatrix} \mathbf{G} \\ \mu \mathbf{I} \end{pmatrix} \delta \mathbf{x} - \begin{pmatrix} \mathbf{d} \\ \mathbf{0} \end{pmatrix} \right\|_2
\]

where \(\mu\) is the damping factor.

### Iterative Relocation

The algorithm performs multiple stages with different distance thresholds, following the approach of Guo & Zhang (2016):

1. Filter triple differences by distance: \(d_{ij} < d_{\text{threshold}}\)
2. Solve for parameter updates: \(\delta \mathbf{x} = \mathbf{G}^+ \mathbf{d}\)
3. Update locations: \(\mathbf{x}_i^{(k+1)} = \mathbf{x}_i^{(k)} + \delta \mathbf{x}_i - \delta \mathbf{x}_{\text{median}}\)
4. Remove median shift to maintain relative positions

The distance filtering helps to:
- Focus on nearby event pairs where the triple difference is most sensitive to relative location
- Reduce computational cost by limiting the number of triple differences
- Improve stability by avoiding poorly constrained long-distance pairs

### Comparison with Double-Difference Method

The triple difference method extends the double-difference approach (Waldhauser & Ellsworth, 2000) by considering differences at station pairs. While the double-difference method uses:

\[
\Delta t_{ij} = t_i - t_j
\]

the triple difference uses:

\[
\Delta \Delta t_{ij,kl} = (t_{i,l} - t_{i,k}) - (t_{j,l} - t_{j,k})
\]

This additional level of differencing further reduces the influence of velocity model errors and common path effects, making it particularly effective for relative relocation of clustered events.

### Parameters
- `iterNum`: Array of iteration numbers for each stage (default: [10, 10])
- `distKm`: Array of distance thresholds in km for each stage (default: [50, 20])
- `dampFact`: Array of damping factors for each stage (default: [0, 1])

---

## 5. CLS Mode: Spatial Clustering

### Overview
The CLS mode performs spatial clustering using the DBSCAN algorithm and calculates triple differences for clustered events. This mode is equivalent to the `ph2dt` program in the `hypoDD` package (Waldhauser & Ellsworth, 2000), which prepares event pairs and differential travel time data for relative relocation.

### DBSCAN Algorithm

DBSCAN (Density-Based Spatial Clustering of Applications with Noise) clusters points based on density:

- **Core point**: A point with at least `minPts` neighbors within distance `eps`
- **Border point**: A point within `eps` of a core point but not a core point itself
- **Noise point**: A point that is neither core nor border

### Distance Metric

The Haversine distance between two points \((\phi_1, \lambda_1)\) and \((\phi_2, \lambda_2)\) is:

\[
d = 2R \arcsin\left(\sqrt{\sin^2\left(\frac{\phi_2 - \phi_1}{2}\right) + \cos(\phi_1) \cos(\phi_2) \sin^2\left(\frac{\lambda_2 - \lambda_1}{2}\right)}\right)
\]

where \(R\) is the Earth's radius (6371 km).

### Triple Difference Calculation

For each cluster, triple differences are calculated for all event pairs and station pairs:

\[
\Delta \Delta t_{ij,kl} = (t_{i,l} - t_{i,k}) - (t_{j,l} - t_{j,k})
\]

These are stored in binary format for use in TRD mode.

### Parameters
- `minPts`: Minimum number of points to form a cluster (default: 3)
- `eps`: Maximum distance between points in a cluster in km (default: 30.0)
- `epsPercentile`: Data inclusion rate when eps < 0 (optional)

---

## 6. SYN Mode: Synthetic Data Generation

### Overview
The SYN mode generates synthetic travel time difference data from a ground truth catalog for testing purposes.

### Data Generation Process

1. **Location Perturbation**: Add random error to true locations:
   \[
   \mathbf{x}_{\text{perturbed}} = \mathbf{x}_{\text{true}} + \boldsymbol{\epsilon}_{\text{loc}}
   \]
   where \(\boldsymbol{\epsilon}_{\text{loc}} \sim \mathcal{N}(\mathbf{0}, \sigma_{\text{loc}}^2 \mathbf{I})\)

2. **Travel Time Calculation**: Calculate travel times using TauP:
   \[
   t_{i,k} = T(\mathbf{x}_{\text{perturbed},i}, \mathbf{s}_k)
   \]

3. **Phase Error**: Add random error to travel times:
   \[
   t_{i,k}^{\text{obs}} = t_{i,k} + \epsilon_{\text{phase}}
   \]
   where \(\epsilon_{\text{phase}} \sim \mathcal{N}(0, \sigma_{\text{phase}}^2)\)

4. **Differential Travel Time**:
   \[
   \Delta t_{i,kl}^{\text{obs}} = t_{i,l}^{\text{obs}} - t_{i,k}^{\text{obs}}
   \]

5. **Data Selection**: Randomly select a fraction of station pairs:
   - Selection rate: \(r \sim \mathcal{U}(r_{\min}, r_{\max})\)

### Parameters
- `randomSeed`: Random seed for reproducibility
- `phsErr`: Phase error standard deviation in seconds (default: 0.15)
- `locErr`: Location error standard deviation in degrees (default: 0.05)
- `minSelectRate`: Minimum selection rate (default: 0.3)
- `maxSelectRate`: Maximum selection rate (default: 0.5)

---

## References

1. Guo, H., & Zhang, H. (2016). Development of a double-difference earthquake location algorithm for mining-induced seismicity. *Geophysical Journal International*, 208(1), 333-348. https://doi.org/10.1093/gji/ggw390

2. Ide, S. (2010). Striations, duration, migration and tidal response in deep tremor. *Nature*, 466(7304), 356-359. http://www-solid.eps.s.u-tokyo.ac.jp/~ide/papers11/ide10.pdf

3. Ohta, Y., Ide, S., & Hino, H. (2019). A method for calculating earthquake source parameters using waveform similarity: Application to the 2011 Tohoku earthquake. *Geophysical Research Letters*, 46(20), 11177-11185. https://doi.org/10.1029/2019GL082468

4. Paige, C. C., & Saunders, M. A. (1982). LSQR: An algorithm for sparse linear equations and sparse least squares. *ACM Transactions on Mathematical Software*, 8(1), 43-71.

5. Waldhauser, F., & Ellsworth, W. L. (2000). A double-difference earthquake location algorithm: Method and application to the northern Hayward fault, California. *Bulletin of the Seismological Society of America*, 90(6), 1353-1368. https://geo.mff.cuni.cz/~jz/prednaska_seismologie/2020_4/cetba/Waldhauser_Ellsworth_BSSA2000.pdf

