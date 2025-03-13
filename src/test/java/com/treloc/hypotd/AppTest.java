package com.treloc.hypotd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
// import org.junit.Before;
import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;
// import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
// import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;

/**
 * Unit test for App.
 */
public class AppTest {
	private static ConfigLoader config;
	private static final Path parentPath = Paths.get("src/test/resources");
	private static final String configFile = parentPath.resolve("config.json").toString();
	private static String[] codeStrings;
	// private double[][] stationTable;

	private static final String iniFile = parentPath.resolve("ini.dat").toString();
	private static final String outFile = parentPath.resolve("out.dat").toString();

	private static final Point pointTrue = new Point(
		"", 39.5, 143.5, 20, 0, 0, 0, 0, "", "SYN", -999
	);

	public AppTest () {}

	@BeforeClass
	public static void setUp() throws TauModelException {
		try {
			config = new ConfigLoader(configFile);
			codeStrings = config.getCodeStrings();
			// stationTable = config.getStationTable();

		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// Generate synthetic data
		config.setMode("SYN");
		SyntheticTest tester = new SyntheticTest(config);
		try {
			tester.generateData(pointTrue, 0.4, 0.6);
		} catch (TauModelException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testSTDLoc () throws TauModelException {
		config.setMode("STD");
		HypoStationPairDiff runner = new HypoStationPairDiff(config);
		try {
			runner.start(iniFile, outFile);
		} catch (TauModelException e) {
			e.printStackTrace();
		}

		PointsHandler pointsHandler = new PointsHandler();
		pointsHandler.readDatFile(outFile, codeStrings, 0);
		Point out = pointsHandler.getMainPoint();
		assertEquals(pointTrue.getLat(), out.getLat(), 3e-1);
		assertEquals(pointTrue.getLon(), out.getLon(), 3e-2);
		assertEquals(pointTrue.getDep(), out.getDep(), 3);
	}

	@Test
	public void testLSQR () {
		OpenMapRealMatrix A = new OpenMapRealMatrix(3, 3);
		A.setEntry(0, 0, 4);
		A.setEntry(1, 1, 5);
		A.setEntry(2, 2, 6);
		double[] b = { 1, 2, 3 };

		ScipyLSQR.LSQRResult result = ScipyLSQR.lsqr(A, b, 0, 1e-6, 1e-6, 1e8, 10, false, false, null);

		assertEquals(0.25, result.x[0], 1e-3);
		assertEquals(0.4, result.x[1], 1e-3);
		assertEquals(0.5, result.x[2], 1e-3);
	}
}
