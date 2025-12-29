package com.treloc.xtreloc.solver;

import org.apache.commons.math3.linear.OpenMapRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
// import org.apache.commons.math3.linear.RealVector;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

/**
 * Sparse Equations and Least Squares.
 * 
 * The original Fortran code was written by
 * 
 * - C. C. Paige and M.A.Saunders,LSQR:
 * An algorithm for sparse linear equations and sparse least squares,TOMS
 * 8(1),43--71(1982).
 * 
 * - C. C. Paige and M.A.Saunders,Algorithm 583;LSQR:
 * Sparse linear equations and least-squares problems, TOMS 8(2),195--209(1982).
 * 
 * It is licensed under the following BSD license:
 * 
 * Copyright (c) 2006, Systems Optimization Laboratory
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * 
 * - Neither the name of Stanford University nor the names of its
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * The Fortran code was translated to Python for use in CVXOPT by Jeffery
 * Kline with contributions by Mridul Aanjaneya and Bob Myhill.
 *
 * This is a Java implementation of the LSQR algorithm by K.M.
 * which is adapted for SciPy by Stefan van der Walt.
 * 
 * @author K.M.
 * @since 2025-02-22
 * @version 0.1
 */

public class ScipyLSQR {

	// Define epsilon from double precision (approximately 2.220446049250313e-16)
	public static final double eps = Math.ulp(1.0);

	public static class SparseLinearOperator {
		private final OpenMapRealMatrix matrix;

		public SparseLinearOperator(OpenMapRealMatrix matrix) {
			this.matrix = matrix;
		}

		public int getRowDimension() {
			return matrix.getRowDimension();
		}

		public int getColumnDimension() {
			return matrix.getColumnDimension();
		}

		public double[] matvec(double[] x) {
			return matrix.operate(x);
		}

		public double[] rmatvec(double[] x) {
			return matrix.transpose().operate(x);
		}
	}

	public static SparseLinearOperator convertToSparseOperator(OpenMapRealMatrix A) {
		return new SparseLinearOperator(A);
	}

	/**
	 * Stable implementation of Givens rotation.
	 *
	 * Notes
	 * -----
	 * The routine 'SymOrtho' was added for numerical stability. This is
	 * recommended by S.-C. Choi in [1]_.
	 *
	 * @param a input value a
	 * @param b input value b
	 * @return an array {c, s, r}
	 */
	public static double[] _sym_ortho(double a, double b) {
		if (b == 0) {
			return new double[] { Math.signum(a), 0, Math.abs(a) };
		} else if (a == 0) {
			return new double[] { 0, Math.signum(b), Math.abs(b) };
		} else if (Math.abs(b) > Math.abs(a)) {
			double tau = a / b;
			double s = Math.signum(b) / Math.sqrt(1 + tau * tau);
			double c = s * tau;
			double r = b / s;
			return new double[] { c, s, r };
		} else {
			double tau = b / a;
			double c = Math.signum(a) / Math.sqrt(1 + tau * tau);
			double s = c * tau;
			double r = a / c;
			return new double[] { c, s, r };
		}
	}

	public static class LSQRResult {
		public double[] x;
		public int istop;
		public int itn;
		public double r1norm;
		public double r2norm;
		public double anorm;
		public double acond;
		public double arnorm;
		public double xnorm;
		public double[] var;

		public LSQRResult(double[] x, int istop, int itn, double r1norm, double r2norm,
				double anorm, double acond, double arnorm, double xnorm, double[] var) {
			this.x = x;
			this.istop = istop;
			this.itn = itn;
			this.r1norm = r1norm;
			this.r2norm = r2norm;
			this.anorm = anorm;
			this.acond = acond;
			this.arnorm = arnorm;
			this.xnorm = xnorm;
			this.var = var;
		}
	}

	/**
	 * Helper function to compute the Euclidean norm of a vector.
	 * 
	 * @param v the vector to compute the norm of
	 * @return the Euclidean norm of the vector
	 */
	public static double norm(double[] v) {
		double sum = 0.0;
		for (double x : v) {
			sum += x * x;
		}
		return Math.sqrt(sum);
	}

	/**
	 * Helper function to perform elementwise addition of two vectors.
	 * 
	 * @param a the first vector
	 * @param b the second vector
	 * @return the elementwise sum of the two vectors
	 */
	public static double[] add(double[] a, double[] b) {
		double[] c = new double[a.length];
		for (int i = 0; i < a.length; i++) {
			c[i] = a[i] + b[i];
		}
		return c;
	}

	/**
	 * Helper function to perform elementwise subtraction of two vectors.
	 * 
	 * @param a the first vector
	 * @param b the second vector
	 * @return the elementwise difference of the two vectors
	 */
	public static double[] subtract(double[] a, double[] b) {
		double[] c = new double[a.length];
		for (int i = 0; i < a.length; i++) {
			c[i] = a[i] - b[i];
		}
		return c;
	}

	/**
	 * Helper function to scale a vector by a scalar.
	 * 
	 * @param a the vector to scale
	 * @param scalar the scalar to multiply the vector by
	 * @return the scaled vector
	 */
	public static double[] scale(double[] a, double scalar) {
		double[] c = new double[a.length];
		for (int i = 0; i < a.length; i++) {
			c[i] = a[i] * scalar;
		}
		return c;
	}

	/**
	 * Finds the least-squares solution to a large, sparse, linear system of
	 * equations.
	 *
	 * <p>
	 * The function solves one of the following problems:
	 * <ul>
	 * <li>Ax = b</li>
	 * <li>min ||Ax - b||^2</li>
	 * <li>min ||Ax - b||^2 + d^2 ||x - x0||^2</li>
	 * </ul>
	 *
	 * The matrix A may be square or rectangular (over-determined or
	 * under-determined)
	 * and may have any rank.
	 *
	 * <h3>Problem Variants:</h3>
	 * <ol>
	 * <li>Unsymmetric equations - Solve Ax = b.</li>
	 * <li>Linear least squares - Solve Ax = b in the least-squares sense.</li>
	 * <li>Damped least squares - Solve in the least-squares sense:
	 * 
	 * <pre>
	 *       ( A   ) * x = ( b   )
	 *       ( damp*I )   ( damp*x0 )
	 * </pre>
	 * 
	 * </li>
	 * </ol>
	 *
	 * @param A       Representation of an m-by-n matrix. Can be a sparse array,
	 *                ndarray, or LinearOperator.
	 * @param b       Right-hand side vector, shape (m,).
	 * @param damp    Damping coefficient. Default is 0.
	 * @param atol    Stopping tolerance.
	 * @param btol    Stopping tolerance.
	 * @param conlim  Additional stopping tolerance.
	 * @param iterLim Explicit limitation on the number of iterations.
	 * @param show    Whether to display an iteration log.
	 * @param calcVar Whether to estimate diagonals of (A'A + damp^2*I)^{-1}.
	 * @param x0      Initial guess for x, shape (n,).
	 * @return {@code LSQRResult} containing x, istop, itn, r1norm, r2norm, anorm,
	 *         acond, arnorm, xnorm, and var.
	 */

	public static LSQRResult lsqr(OpenMapRealMatrix A, double[] b, double damp, double atol, double btol,
			double conlim, Integer iter_lim, boolean show, boolean calc_var, double[] x0) {
		return lsqr(A, b, damp, atol, btol, conlim, iter_lim, show, calc_var, x0, null);
	}
	
	public static LSQRResult lsqr(OpenMapRealMatrix A, double[] b, double damp, double atol, double btol,
			double conlim, Integer iter_lim, boolean show, boolean calc_var, double[] x0, java.util.function.Consumer<String> logConsumer) {
		SparseLinearOperator Aop = convertToSparseOperator(A);
		int m = Aop.getRowDimension();
		int n = Aop.getColumnDimension();
		if (iter_lim == null) {
			iter_lim = 2 * n;
		}
		double[] var = new double[n];
		String[] msg = {
				"The exact solution is  x = 0                              ",
				"Ax - b is small enough, given atol, btol                  ",
				"The least-squares solution is good enough, given atol     ",
				"The estimate of cond(Abar) has exceeded conlim            ",
				"Ax - b is small enough for this machine                   ",
				"The least-squares solution is good enough for this machine",
				"Cond(Abar) seems to be too large for this machine         ",
				"The iteration limit has been reached                      "
		};

		if (show) {
			String line1 = " ";
			String line2 = "LSQR            Least-squares solution of  Ax = b";
			String str1 = "The matrix A has " + m + " rows and " + n + " columns";
			String str2 = String.format("damp = %20.14e   calc_var = %8b", damp, calc_var);
			String str3 = String.format("atol = %8.2e                 conlim = %8.2e", atol, conlim);
			String str4 = String.format("btol = %8.2e               iter_lim = %8d", btol, iter_lim);
			System.out.println(line1);
			System.out.println(line2);
			System.out.println(str1);
			System.out.println(str2);
			System.out.println(str3);
			System.out.println(str4);
			if (logConsumer != null) {
				logConsumer.accept(line1);
				logConsumer.accept(line2);
				logConsumer.accept(str1);
				logConsumer.accept(str2);
				logConsumer.accept(str3);
				logConsumer.accept(str4);
			}
		}

		int itn = 0;
		int istop = 0;
		double ctol = 0;
		if (conlim > 0) {
			ctol = 1 / conlim;
		}
		double anorm = 0;
		double acond = 0;
		double dampsq = damp * damp;
		double ddnorm = 0;
		double res2 = 0;
		double xnorm = 0;
		double xxnorm = 0;
		double z = 0;
		double cs2 = -1;
		double sn2 = 0;

		// Set up the first vectors u and v for the bidiagonalization.
		// These satisfy beta*u = b - A@x, alfa*v = A'@u.
		double[] u = Arrays.copyOf(b, b.length);
		double bnorm = norm(b);

		double[] x;
		double beta;
		if (x0 == null) {
			x = new double[n];
			beta = bnorm;
		} else {
			x = Arrays.copyOf(x0, x0.length);
			double[] Ax = Aop.matvec(x);
			u = subtract(u, Ax);
			beta = norm(u);
		}

		double alfa = 0;
		double[] v;
		if (beta > 0) {
			u = scale(u, 1.0 / beta);
			v = Aop.rmatvec(u);
			alfa = norm(v);
		} else {
			v = Arrays.copyOf(x, x.length);
			alfa = 0;
		}

		if (alfa > 0) {
			v = scale(v, 1.0 / alfa);
		}
		double[] w = Arrays.copyOf(v, v.length);

		double rhobar = alfa;
		double phibar = beta;
		double rnorm = beta;
		double r1norm = rnorm;
		double r2norm = rnorm;

		// Reverse the order here from the original matlab code because
		// there was an error on return when arnorm==0
		double arnorm = alfa * beta;
		if (arnorm == 0) {
			if (show) {
				System.out.println(msg[0]);
				if (logConsumer != null) {
					logConsumer.accept(msg[0]);
				}
			}
			return new LSQRResult(x, istop, itn, r1norm, r2norm, anorm, acond, arnorm, xnorm, var);
		}

		String head1 = "   Itn      x[0]       r1norm     r2norm ";
		String head2 = " Compatible    LS      Norm A   Cond A";

		if (show) {
			String line1 = " ";
			String line2 = head1 + head2;
			double test1 = 1;
			double test2 = alfa / beta;
			String str1 = String.format("%6d %12.5e", itn, x[0]);
			String str2 = String.format(" %10.3e %10.3e", r1norm, r2norm);
			String str3 = String.format("  %8.1e %8.1e", test1, test2);
			String line3 = str1 + str2 + str3;
			System.out.println(line1);
			System.out.println(line2);
			System.out.println(line3);
			if (logConsumer != null) {
				logConsumer.accept(line1);
				logConsumer.accept(line2);
				logConsumer.accept(line3);
			}
		}

		// Main iteration loop.
		while (itn < iter_lim) {
			// Check for interruption
			if (Thread.currentThread().isInterrupted()) {
				throw new RuntimeException("LSQR computation was interrupted");
			}
			
			itn = itn + 1;
			// Perform the next step of the bidiagonalization to obtain the
			// next beta, u, alfa, v. These satisfy the relations
			// beta*u = A@v - alfa*u,
			// alfa*v = A'@u - beta*v.
			double[] Av = Aop.matvec(v);
			double[] scaledU = scale(u, alfa);
			u = subtract(Av, scaledU);
			beta = norm(u);

			if (beta > 0) {
				u = scale(u, 1.0 / beta);
				anorm = Math.sqrt(anorm * anorm + alfa * alfa + beta * beta + dampsq);
				double[] Atu = Aop.rmatvec(u);
				double[] scaledV = scale(v, beta);
				v = subtract(Atu, scaledV);
				alfa = norm(v);
				if (alfa > 0) {
					v = scale(v, 1.0 / alfa);
				}
			}

			// Use a plane rotation to eliminate the damping parameter.
			// This alters the diagonal (rhobar) of the lower-bidiagonal matrix.
			double rhobar1;
			double cs1;
			double sn1;
			double psi;
			if (damp > 0) {
				rhobar1 = Math.sqrt(rhobar * rhobar + dampsq);
				cs1 = rhobar / rhobar1;
				sn1 = damp / rhobar1;
				psi = sn1 * phibar;
				phibar = cs1 * phibar;
			} else {
				// cs1 = 1 and sn1 = 0
				rhobar1 = rhobar;
				psi = 0.0;
			}

			// Use a plane rotation to eliminate the subdiagonal element (beta)
			// of the lower-bidiagonal matrix, giving an upper-bidiagonal matrix.
			double[] symOrtho = _sym_ortho(rhobar1, beta);
			double cs = symOrtho[0];
			double sn = symOrtho[1];
			double rho = symOrtho[2];

			double theta = sn * alfa;
			rhobar = -cs * alfa;
			double phi = cs * phibar;
			phibar = sn * phibar;
			double tau = sn * phi;

			// Update x and w.
			double t1 = phi / rho;
			double t2 = -theta / rho;
			double[] dk = scale(w, 1.0 / rho);

			x = add(x, scale(w, t1));
			w = add(v, scale(w, t2));
			ddnorm = ddnorm + norm(dk) * norm(dk);

			if (calc_var) {
				for (int i = 0; i < var.length; i++) {
					var[i] = var[i] + dk[i] * dk[i];
				}
			}

			// Use a plane rotation on the right to eliminate the
			// super-diagonal element (theta) of the upper-bidiagonal matrix.
			// Then use the result to estimate norm(x).
			double delta = sn2 * rho;
			double gambar = -cs2 * rho;
			double rhs = phi - delta * z;
			double zbar = rhs / gambar;
			xnorm = Math.sqrt(xxnorm + zbar * zbar);
			double gamma = Math.sqrt(gambar * gambar + theta * theta);
			cs2 = gambar / gamma;
			sn2 = theta / gamma;
			z = rhs / gamma;
			xxnorm = xxnorm + z * z;

			// Test for convergence.
			// First, estimate the condition of the matrix Abar,
			// and the norms of rbar and Abar'rbar.
			acond = anorm * Math.sqrt(ddnorm);
			double res1 = phibar * phibar;
			res2 = res2 + psi * psi;
			rnorm = Math.sqrt(res1 + res2);
			arnorm = alfa * Math.abs(tau);

			// Distinguish between
			// r1norm = ||b - Ax|| and
			// r2norm = rnorm in current code
			// = sqrt(r1norm^2 + damp^2*||x - x0||^2).
			// Estimate r1norm from
			// r1norm = sqrt(r2norm^2 - damp^2*||x - x0||^2).
			if (damp > 0) {
				double r1sq = rnorm * rnorm - dampsq * xxnorm;
				r1norm = Math.sqrt(Math.abs(r1sq));
				if (r1sq < 0) {
					r1norm = -r1norm;
				}
			} else {
				r1norm = rnorm;
			}
			r2norm = rnorm;

			// Now use these norms to estimate certain other quantities,
			// some of which will be small near a solution.
			double test1 = rnorm / bnorm;
			double test2 = arnorm / (anorm * rnorm + eps);
			double test3 = 1 / (acond + eps);
			t1 = test1 / (1 + anorm * xnorm / bnorm);
			double rtol = btol + atol * anorm * xnorm / bnorm;

			// The following tests guard against extremely small values of
			// atol, btol or ctol.
			if (itn >= iter_lim) {
				istop = 7;
			}
			if (1 + test3 <= 1) {
				istop = 6;
			}
			if (1 + test2 <= 1) {
				istop = 5;
			}
			if (1 + t1 <= 1) {
				istop = 4;
			}

			// Allow for tolerances set by the user.
			if (test3 <= ctol) {
				istop = 3;
			}
			if (test2 <= atol) {
				istop = 2;
			}
			if (test1 <= rtol) {
				istop = 1;
			}

			if (show) {
				boolean prnt = false;
				if (n <= 40) {
					prnt = true;
				}
				if (itn <= 10) {
					prnt = true;
				}
				if (itn >= iter_lim - 10) {
					prnt = true;
				}
				if (test3 <= 2 * ctol) {
					prnt = true;
				}
				if (test2 <= 10 * atol) {
					prnt = true;
				}
				if (test1 <= 10 * rtol) {
					prnt = true;
				}
				if (istop != 0) {
					prnt = true;
				}

				if (prnt) {
					String str1 = String.format("%6d %12.5e", itn, x[0]);
					String str2 = String.format(" %10.3e %10.3e", r1norm, r2norm);
					String str3 = String.format("  %8.1e %8.1e", test1, test2);
					String str4 = String.format(" %8.1e %8.1e", anorm, acond);
					String line = str1 + str2 + str3 + str4;
					System.out.println(line);
					if (logConsumer != null) {
						logConsumer.accept(line);
					}
				}
			}

			if (istop != 0) {
				break;
			}
		}

		if (show) {
			String line1 = " ";
			String line2 = "LSQR finished";
			String line3 = msg[istop];
			String line4 = " ";
			String str1 = String.format("istop =%8d   r1norm =%8.1e", istop, r1norm);
			String str2 = String.format("anorm =%8.1e   arnorm =%8.1e", anorm, arnorm);
			String str3 = String.format("itn   =%8d   r2norm =%8.1e", itn, r2norm);
			String str4 = String.format("acond =%8.1e   xnorm  =%8.1e", acond, xnorm);
			String line5 = str1 + "   " + str2;
			String line6 = str3 + "   " + str4;
			String line7 = " ";
			System.out.println(line1);
			System.out.println(line2);
			System.out.println(line3);
			System.out.println(line4);
			System.out.println(line5);
			System.out.println(line6);
			System.out.println(line7);
			if (logConsumer != null) {
				logConsumer.accept(line1);
				logConsumer.accept(line2);
				logConsumer.accept(line3);
				logConsumer.accept(line4);
				logConsumer.accept(line5);
				logConsumer.accept(line6);
				logConsumer.accept(line7);
			}
		}
		return new LSQRResult(x, istop, itn, r1norm, r2norm, anorm, acond, arnorm, xnorm, var);
	}

	public static void main(String[] args) {
		int m = 20;
		int n = 10;
		double sparsity = 0.1;
		OpenMapRealMatrix A = SparseMatrixGenerator.generateSparseMatrix(m, n, sparsity);
		double[] b = SparseMatrixGenerator.generateVector(m);

		SparseMatrixGenerator.saveMatrixToCSV(A, "matrix_A.csv");
		SparseMatrixGenerator.saveVectorToCSV(b, "vector_b.csv");

		LSQRResult result = lsqr(A, b, 0, 1e-6, 1e-6, 1e8, 10, true, false, null);

		System.out.println("Solution: " + Arrays.toString(result.x));
	}
}

class SparseMatrixGenerator {
	/**
	 * Generates a sparse matrix with the specified number of rows, columns, and sparsity.
	 * 
	 * @param m the number of rows in the matrix
	 * @param n the number of columns in the matrix
	 * @param sparsity the sparsity of the matrix (0 to 1)
	 * @return the generated sparse matrix
	 */
	public static OpenMapRealMatrix generateSparseMatrix(int m, int n, double sparsity) {
		OpenMapRealMatrix A = new OpenMapRealMatrix(m, n);
		Random rand = new Random();

		int nonZeroElements = (int) (m * n * sparsity);
		for (int i = 0; i < nonZeroElements; i++) {
			int row = rand.nextInt(m);
			int col = rand.nextInt(n);
			double value = rand.nextDouble() * 10 - 5; // Range of [-5, 5]
			A.setEntry(row, col, value);
		}
		return A;
	}

	public static double[] generateVector(int n) {
		double[] b = new double[n];
		Random rand = new Random();
		for (int i = 0; i < n; i++) {
			b[i] = rand.nextDouble() * 10 - 5;
		}
		return b;
	}

	public static void saveMatrixToCSV(RealMatrix matrix, String filename) {
		try (FileWriter writer = new FileWriter(filename)) {
			int rows = matrix.getRowDimension();
			int cols = matrix.getColumnDimension();
			for (int i = 0; i < rows; i++) {
				for (int j = 0; j < cols; j++) {
					writer.write(matrix.getEntry(i, j) + (j < cols - 1 ? "," : ""));
				}
				writer.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Saves a vector to a CSV file.
	 * 
	 * @param vector the vector to save
	 * @param filename the name of the file to save the vector to
	 */
	public static void saveVectorToCSV(double[] vector, String filename) {
		try (FileWriter writer = new FileWriter(filename)) {
			for (double v : vector) {
				writer.write(v + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Prints a sample of the matrix entries.
	 * 
	 * @param matrix the matrix to print a sample of
	 * @param sampleSize the number of entries to print
	 */
	public static void printSample(RealMatrix matrix, int sampleSize) {
		Random rand = new Random();
		int rows = matrix.getRowDimension();
		int cols = matrix.getColumnDimension();

		for (int i = 0; i < sampleSize; i++) {
			int row = rand.nextInt(rows);
			int col = rand.nextInt(cols);
			double value = matrix.getEntry(row, col);
			if (value != 0.0) {
				System.out.printf("A[%d][%d] = %.3f%n", row, col, value);
			}
		}
	}
}
