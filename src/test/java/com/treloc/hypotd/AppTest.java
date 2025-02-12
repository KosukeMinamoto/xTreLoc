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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
// import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;

/**
 * Unit test for App.
 */
public class AppTest {
	private AppConfig config;
	private final Path parentPath = Paths.get("src/test/resources");
	private final String configFile = parentPath.resolve("config.json").toString();
	private final String stnFile = parentPath.resolve("station.tbl").toString();
	private final String iniFile = parentPath.resolve("ini.dat").toString();
	private final String outFile = parentPath.resolve("out.dat").toString();
	
	private final double truLat = 39.5;
	private final double truLon = 143.5;
	private final double truDep = 20;

	private final String[] codes = {"ST01","ST02","ST03","ST04","ST05","ST06","ST07","ST08","ST09","ST10","ST11","ST12"};
	private final double[][] stnTable = {
			{ 39.00, 142.20, 1.00, 0.40, 0.68 },
			{ 39.50, 142.20, 1.67, 0.88, 1.50 },
			{ 40.00, 142.20, 2.33, 0.97, 1.65 },
			{ 39.00, 142.53, 3.00, 0.48, 0.81 },
			{ 39.50, 142.53, 1.00, 0.16, 0.27 },
			{ 40.00, 142.53, 1.67, 0.72, 1.23 },
			{ 39.00, 142.87, 2.33, 0.89, 1.51 },
			{ 39.50, 142.87, 3.00, 0.56, 0.95 },
			{ 40.00, 142.87, 1.00, 0.35, 0.59 },
			{ 39.00, 143.20, 1.67, 0.80, 1.36 },
			{ 39.50, 143.20, 2.33, 0.19, 0.32 },
			{ 40.00, 143.20, 3.00, 0.38, 0.64 },
	};

	@Before
	public void setUp() {
		System.out.println("==================================");
		System.out.println("> Creating config file...");
		this.config = new AppConfig();
		config.setStnFile(stnFile);
		config.setTaumodFile("prem");
		config.setCatalogFile("test.list");
		config.setDatPattern(iniFile);
		config.setNumJobs(2);
		config.setNumGrid(100);
		config.setHypBottom(50);
		config.setThreshold(0.2);
		config.setStnBottom(1);
		config.setStationTable(stnTable);
		config.setCodes(codes);

		try {
			config.writeConfig(configFile);
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("> Creating station table...");

		config.writeStnTable(stnFile);

		System.out.println("> Creating lagt file...");

		// Dat file
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

		SyntheticTest tester = new SyntheticTest(config);
		LocalDateTime time = LocalDateTime.parse("2000-01-01T00:00:00");
		try {
			tester.generateData(truLon, truLat, truDep, time, iniFile, 0.4, 0.6);
		} catch (TauModelException e) {
			e.printStackTrace();
		}
		System.out.println("> Data creation end");
		System.out.println("==================================");
	}

	// @Test
	// public void testReadConfig() throws IOException {
	// 	this.config = new AppConfig();
	// 	config.readConfig(configFile);
	// 	String taumodFile = "prem";
	// 	assertEquals(taumodFile, config.getTaumodFile());
	// }

	@Test
	public void testSTDLoc () {
		HypoStationPairDiff runner = new HypoStationPairDiff(config);
		try {
			runner.start(iniFile, outFile);
		} catch (TauModelException e) {
			e.printStackTrace();
		}

		PointsHandler pointsHandler = new PointsHandler();
		pointsHandler.readDatFile(outFile, codes, 0);
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
