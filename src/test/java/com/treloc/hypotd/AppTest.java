package com.treloc.hypotd;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;

import org.junit.Test;
import org.junit.Before;
// import org.junit.BeforeClass;

import static org.junit.Assert.assertEquals;
// import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
// import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;

/**
 * Unit test for App.
 */
public class AppTest {
	private ConfigLoader config;
	private final Path parentPath = Paths.get("src/test/resources");
	private final String configFile = parentPath.resolve("config.json").toString();
	private String[] codeStrings;
	// private double[][] stationTable;

	private final String iniFile = parentPath.resolve("ini.dat").toString();
	private final String outFile = parentPath.resolve("out.dat").toString();

	private final double truLat = 39.5;
	private final double truLon = 143.5;
	private final double truDep = 20;
	private final String trTime = "2000-01-01T00:00:00";

	public AppTest () throws IOException {
		config = new ConfigLoader(configFile);
		codeStrings = config.getCodeStrings();
		// stationTable = config.getStationTable();
	}

	@Before
	public void setUp() {
		System.out.println("================================");
		System.out.println("========== Setting up ==========");

		SyntheticTest tester = new SyntheticTest(config);
		LocalDateTime time = LocalDateTime.parse(trTime);
		try {
			tester.generateData(truLon, truLat, truDep, time, iniFile, 0.4, 0.6);
		} catch (TauModelException e) {
			e.printStackTrace();
		}
		System.out.println("================================");
	}

	@Test
	public void testSTDLoc () {
		HypoStationPairDiff runner = new HypoStationPairDiff(config);
		try {
			runner.start(iniFile, outFile);
		} catch (TauModelException e) {
			e.printStackTrace();
		}

		PointsHandler pointsHandler = new PointsHandler();
		pointsHandler.readDatFile(outFile, codeStrings, 0);
		Point out = pointsHandler.getMainPoint();
		assertEquals(truLat, out.getLat(), 3e-1);
		assertEquals(truLon, out.getLon(), 3e-2);
		assertEquals(truDep, out.getDep(), 3);
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
