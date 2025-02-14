package com.treloc.hypotd;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
// import org.apache.commons.math3.fitting.leastsquares.EvaluationRmsChecker;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
// import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.util.Precision;
import org.apache.commons.math3.linear.SingularMatrixException;
import edu.sc.seis.TauP.TauModelException;

/*
 * Hypocenter location with Levenberg-Marquardt algorithm
 * based on Ide (2010) & Ohta et al. (2019)
 * 
 * @author: K.M.
 * @date: 2025/01/26
 * @version: 0.1
 * @description: The class is used to calculate the hypocenter location 
 * using Levenberg-Marquardt algorithm.
 * @usage: 
 */

public class HypoStationPairDiff extends HypoUtils {
	private final double[][] stationTable;
	private final String[] codeStrings;
	private final double hypBottom, stnBottom, threshold;

	public HypoStationPairDiff(ConfigLoader appConfig) {
		super(appConfig);
		codeStrings = appConfig.getCodeStrings();
		stationTable = appConfig.getStationTable();
		hypBottom = appConfig.getHypBottom();
		stnBottom = appConfig.getStnBottom();
		threshold = appConfig.getThreshold();
	}

	public void start(String datFile, String outFile) throws TauModelException {
		PointsHandler pointsHandler = new PointsHandler();
		pointsHandler.readDatFile(datFile, codeStrings, threshold);
		Point point = pointsHandler.getMainPoint();

		double[][] lagTable = point.getLagTable();
		int[] usedIdx = point.getUsedIdx();
		int numPhase = lagTable.length;
		double lat = point.getLat();
		double lon = point.getLon();
		double dep = point.getDep();
		double eLat = point.getElat();
		double eLon = point.getElon();
		double eDep = point.getEdep();
		double res = point.getRes();
		String method = point.getType();

		// Initial hypocenter
		RealVector hypvec = MatrixUtils.createRealVector(new double[] {lon, lat, dep});
		RealVector target = MatrixUtils.createRealVector(new double[numPhase]);
		for ( int i = 0; i < numPhase; i++ ) {
			target.setEntry(i, lagTable[i][2]);
		}

		// Iteration to remove outliers
		boolean hasOutlier = false;
		boolean[] isOutlier = new boolean[numPhase];
		for (int n = 0; n < 10; n++) {
			LeastSquaresProblem problem = new LeastSquaresBuilder()
					.start(hypvec)
					// .weight(weight)
					.target(target)
					.model(getPartialDerivativeFunction(lagTable, usedIdx))
					// .checker(new EvaluationRmsChecker(2))
					.lazyEvaluation(false)
					.maxEvaluations(1000)
					.maxIterations(1000)
					.parameterValidator(new ParameterValidator() {
						@Override
						public RealVector validate(RealVector params) {
							if (params.getEntry(2) <= stnBottom){ // Airquake
								params.setEntry(2, Math.random()*hypBottom);
							}
							return params;
						}
					})
					.build();
			LeastSquaresOptimizer.Optimum optimum = new LevenbergMarquardtOptimizer(
				100,
				1e-6,
				1e-6,
				1e-6,
				Precision.SAFE_MIN
			).optimize(problem);

			// res = optimum.getRMS();

			RealVector resDiffTime = optimum.getResiduals();
			double sumOfSquares = 0;
			int okCount = 0;
			for (int i = 0; i < numPhase; i++) {
				if ( !isOutlier[i] ) {
					sumOfSquares += Math.pow(resDiffTime.getEntry(i), 2);
					okCount++;
				}
			}
			res = Math.sqrt(sumOfSquares / okCount);

			for (int i = 0; i < numPhase; i++) {
				if ( !isOutlier[i] ) {
					if (Math.abs(resDiffTime.getEntry(i)) > 2 * res) {
						lagTable[i][3] = 0;
						isOutlier[i] = true;
						hasOutlier = true;
					}
				}
			}

			if ( hasOutlier ) {
				hasOutlier = false;
				continue;
			} else {
				double[] xmin = optimum.getPoint().toArray();
				lon = xmin[0];
				lat = xmin[1];
				dep = xmin[2];
				method = "STD";

				// Error estimation
				boolean success = false;
				RealVector sigma = new ArrayRealVector(3);
				try {
					RealMatrix fjac = optimum.getJacobian();
					RealMatrix rtr = fjac.transpose().multiply(fjac);
					LUDecomposition luDecomposition = new LUDecomposition(rtr);
					RealMatrix err = luDecomposition.getSolver().getInverse().scalarMultiply(res * res);
					for ( int i = 0; i<3; i++ ) {
						sigma.setEntry(i, Math.sqrt(err.getEntry(i, i)));
					}
					success = true;
				} catch (SingularMatrixException e) {
					// pass 
				}
 
				if ( !success ) {
					try {
						RealVector tmp = optimum.getSigma(1e-10);
						for (int i = 0; i < 3; i++) {
							sigma.setEntry(i, tmp.getEntry(i) * res);
						}
					} catch	( SingularMatrixException e ) {
						for (int i = 0; i < 3; i++) {
							sigma.setEntry(i, 999);
						}
					}
				}

				eLon = sigma.getEntry(0) * App.deg2km * Math.cos(Math.toRadians(lat));
				eLat = sigma.getEntry(1) * App.deg2km;
				eDep = sigma.getEntry(2);

				System.out.println("> Evaluations: " + optimum.getEvaluations());
				System.out.println("> Iterations: " + optimum.getIterations());
				break;
			}
		}

		point.setLat(lat);
		point.setLon(lon);
		point.setDep(dep);
		point.setElat(eLat);
		point.setElon(eLon);
		point.setEdep(eDep);
		point.setRes(res);
		point.setType(method);
		point.setLagTable(lagTable);
		pointsHandler.writeDatFile(outFile, codeStrings);
	}

	public MultivariateJacobianFunction getPartialDerivativeFunction(double[][] lagTable, int[] usedIdx) {
		return new MultivariateJacobianFunction() {
			public Pair<RealVector, RealMatrix> value(RealVector hypoVector) {
				RealVector value = new ArrayRealVector(lagTable.length);
				RealMatrix jacobian = new Array2DRowRealMatrix(lagTable.length, 3);

				try {
					Object[] tmp = partialDerivativeMatrix(stationTable, usedIdx, hypoVector);
					double[][] dtdr = (double[][]) tmp[0];
					double[] trvTime = (double[]) tmp[1];

					for (int i = 0; i < lagTable.length; i++) {
						int nstnk = (int) lagTable[i][0];
						int nstnl = (int) lagTable[i][1];
						if ( (int) lagTable[i][3] == 0 ) { // Outlier
							value.setEntry(i, 0);
							jacobian.setEntry(i, 0, 0); // dt/dx
							jacobian.setEntry(i, 1, 0); // dt/dy
							jacobian.setEntry(i, 2, 0); // dt/dz
						} else {
							// When the TauP tool returning NaN values for the takeoff angle
							// (for unknown reasons), equations are excluded
							// if (containsNaN(dtdr[nstnk]) | containsNaN(dtdr[nstnl]))
							// {
							// value.setEntry(i, 0);
							// jacobian.setEntry(i, 0, 0.0);
							// jacobian.setEntry(i, 1, 0.0);
							// jacobian.setEntry(i, 2, 0.0);
							// System.out.println(hypoVector.getEntry(1) + " " + hypoVector.getEntry(0) + "
							// " + hypoVector.getEntry(2) + " @ " + nstnk + " " + nstnl);
							// } else if (lagTable[i][3] < 0.001) {
							// value.setEntry(i, 0);
							// jacobian.setEntry(i, 0, 0.0);
							// jacobian.setEntry(i, 1, 0.0);
							// jacobian.setEntry(i, 2, 0.0);
							// } else {
							value.setEntry(i, trvTime[nstnl] - trvTime[nstnk]);
							jacobian.setEntry(i, 0, dtdr[nstnl][0] - dtdr[nstnk][0]); // dt/dx
							jacobian.setEntry(i, 1, dtdr[nstnl][1] - dtdr[nstnk][1]); // dt/dy
							jacobian.setEntry(i, 2, dtdr[nstnl][2] - dtdr[nstnk][2]); // dt/dz
							// }
						}
					}
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
				return new Pair<RealVector, RealMatrix>(value, jacobian);
			}
		};
	}
}