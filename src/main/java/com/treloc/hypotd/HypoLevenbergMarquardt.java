package com.treloc.hypotd;

// import java.util.Arrays;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
// import org.apache.commons.math3.fitting.leastsquares.EvaluationRmsChecker;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.util.Precision;
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

public class HypoLevenbergMarquardt {
	private final AppConfig appConfig;
	private final HypoUtils hypoUtils;

	private final double[][] stnTable;
	private final String[] allCodes;
	private final double deepest;

	public HypoLevenbergMarquardt(AppConfig config) throws Exception {
		appConfig = config;
		deepest = config.getDepLim();
		hypoUtils = new HypoUtils(appConfig);
		stnTable = appConfig.getStationTable();
		allCodes = appConfig.getCodes();
	}

	public void start(String datFile, String outFile) throws TauModelException {
		DataHandler dataHandler = new DataHandler(appConfig);
		dataHandler.read(datFile, allCodes);

		double[][] lagTable = dataHandler.getLagTable();
		int[] usedIdx = dataHandler.getUsedIdx();
		int numPhase = lagTable.length;
		double lat = dataHandler.getLatitude();
		double lon = dataHandler.getLongitude();
		double dep = dataHandler.getDepth();
		double eLat = dataHandler.getLatitudeError();
		double eLon = dataHandler.getLongitudeError();
		double eDep = dataHandler.getDepthError();
		double res = dataHandler.getResidual();
		String method = dataHandler.getMethod();

		// Initial hypocenter
		RealVector hypvec = MatrixUtils.createRealVector(new double[] {lon, lat, dep});
		RealVector target = MatrixUtils.createRealVector(new double[numPhase]);
		RealMatrix weight = new DiagonalMatrix(numPhase);
		for (int i = 0; i < numPhase; i++) {
			target.setEntry(i, lagTable[i][2]);
			weight.setEntry(i, i, lagTable[i][3]);
		}

		// Iteration to remove outliers
		boolean hasOutlier = false;
		for (int n = 0; n < 5; n++) {
			LeastSquaresProblem problem = new LeastSquaresBuilder()
					.start(hypvec)
					.weight(weight)
					.target(target)
					.model(getPartialDerivativeFunction(lagTable, usedIdx))
					// .checker(new EvaluationRmsChecker(2))
					.lazyEvaluation(false)
					.maxEvaluations(1000)
					.maxIterations(1000)
					.parameterValidator(new ParameterValidator() {
						@Override
						public RealVector validate(RealVector params) {
							if (params.getEntry(2) < 0){ // Airquake
								params.setEntry(2, Math.random()*deepest);
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

			RealVector resDiffTime = optimum.getResiduals();
			double[] newWeight = new double[numPhase];
			res = optimum.getRMS();
			for (int i = 0; i < numPhase; i++) {
				if (Math.abs(resDiffTime.getEntry(i)) > 3*res) {
					lagTable[i][3] = 0;
					newWeight[i] = 0;
					hasOutlier = true;
				} else {
					lagTable[i][3] = resDiffTime.getEntry(i);
					newWeight[i] = 1;
				}
			}

			if ( hasOutlier ) {
				weight = new DiagonalMatrix(newWeight);
				hasOutlier = false;
				continue;
			} else {
				double[] xmin = optimum.getPoint().toArray();
				lon = xmin[0];
				lat = xmin[1];
				dep = xmin[2];
				method = "LMO";

				RealMatrix fjac = optimum.getJacobian();
				RealMatrix rtr = fjac.transpose().multiply(fjac);
				LUDecomposition luDecomposition = new LUDecomposition(rtr);
				RealMatrix inverseRtr = luDecomposition.getSolver().getInverse();

				double[] sigma = new double[3];
				for (int i = 0; i < 3; i++) {
					sigma[i] = res * Math.sqrt(inverseRtr.getEntry(i, i));
				}
				eLon = sigma[0] * App.deg2km * Math.cos(Math.toRadians(lat));
				eLat = sigma[1] * App.deg2km;
				eDep = sigma[2];
				// System.out.println("Evaluations: " + optimum.getEvaluations());
				// System.out.println("Iterations: " + optimum.getIterations());
				break;
			}
		}

		dataHandler.setLatitude(lat);
		dataHandler.setLongitude(lon);
		dataHandler.setDepth(dep);
		dataHandler.setLatitudeError(eLat);
		dataHandler.setLongitudeError(eLon);
		dataHandler.setDepthError(eDep);
		dataHandler.setResidual(res);
		dataHandler.setMethod(method);
		dataHandler.setLagTable(lagTable);
		dataHandler.write(outFile, allCodes);
	}

	public MultivariateJacobianFunction getPartialDerivativeFunction(double[][] lagTable, int[] usedIdx) {
		return new MultivariateJacobianFunction() {
			public Pair<RealVector, RealMatrix> value(RealVector hypoVector) {
				RealVector value = new ArrayRealVector(lagTable.length);
				RealMatrix jacobian = new Array2DRowRealMatrix(lagTable.length, 3);

				try {
					Object[] tmp = hypoUtils.partialDerivativeMatrix(stnTable, usedIdx, hypoVector);
					double[][] dtdr = (double[][]) tmp[0];
					double[] trvTime = (double[]) tmp[1];

					for (int i = 0; i < lagTable.length; i++) {
						int nstnk = (int) lagTable[i][0];
						int nstnl = (int) lagTable[i][1];

						// When the TauP tool returning NaN values for the takeoff angle
						// (for unknown reasons), equations are excluded
						// if (HypoUtils.containsNaN(dtdr[nstnk]) | HypoUtils.containsNaN(dtdr[nstnl])) {
						// 	value.setEntry(i, 0);
						// 	jacobian.setEntry(i, 0, 0.0);
						// 	jacobian.setEntry(i, 1, 0.0);
						// 	jacobian.setEntry(i, 2, 0.0);
						// 	System.out.println(hypoVector.getEntry(1) + " " + hypoVector.getEntry(0) + " " + hypoVector.getEntry(2) + " @ " + nstnk + " " + nstnl);
						// } else if (lagTable[i][3] < 0.001) {
						// 	value.setEntry(i, 0);
						// 	jacobian.setEntry(i, 0, 0.0);
						// 	jacobian.setEntry(i, 1, 0.0);
						// 	jacobian.setEntry(i, 2, 0.0);
						// } else {
						value.setEntry(i, trvTime[nstnl] - trvTime[nstnk]);
						jacobian.setEntry(i, 0, dtdr[nstnl][0] - dtdr[nstnk][0]); // dt/dx
						jacobian.setEntry(i, 1, dtdr[nstnl][1] - dtdr[nstnk][1]); // dt/dy
						jacobian.setEntry(i, 2, dtdr[nstnl][2] - dtdr[nstnk][2]); // dt/dz
						// }
					}
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
				return new Pair<RealVector, RealMatrix>(value, jacobian);
			}
		};
	}
}