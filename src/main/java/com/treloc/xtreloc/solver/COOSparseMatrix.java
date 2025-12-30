package com.treloc.xtreloc.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * Sparse matrix implementation using COO (Coordinate Format).
 * 
 * <p>This class represents a sparse matrix in COO format to avoid array size limitations.
 * Only non-zero elements are stored as (row, col, value) tuples, providing good memory
 * efficiency and avoiding array size limitations even for large matrices.
 * 
 * <p>This implementation supports matvec and rmatvec operations for use with the LSQR algorithm.
 * 
 * @author xTreLoc Development Team
 * @version 1.0
 */
public class COOSparseMatrix {
    private final int rows;
    private final int cols;
    private final List<Entry> entries;
    
    private static class Entry {
        final int row;
        final int col;
        final double value;
        
        Entry(int row, int col, double value) {
            this.row = row;
            this.col = col;
            this.value = value;
        }
    }
    
    /**
     * Creates a sparse matrix with the specified number of rows and columns.
     * 
     * @param rows number of rows
     * @param cols number of columns
     */
    public COOSparseMatrix(int rows, int cols) {
        if (rows < 0 || cols < 0) {
            throw new IllegalArgumentException("Matrix dimensions must be non-negative");
        }
        this.rows = rows;
        this.cols = cols;
        this.entries = new ArrayList<>();
    }
    
    /**
     * Returns the number of rows in the matrix.
     * 
     * @return number of rows
     */
    public int getRowDimension() {
        return rows;
    }
    
    /**
     * Returns the number of columns in the matrix.
     * 
     * @return number of columns
     */
    public int getColumnDimension() {
        return cols;
    }
    
    /**
     * Sets the value at the specified position.
     * 
     * @param row row index (0-based)
     * @param col column index (0-based)
     * @param value value to set
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public void setEntry(int row, int col, double value) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException(
                String.format("Index out of bounds: row=%d (max=%d), col=%d (max=%d)", 
                    row, rows - 1, col, cols - 1));
        }
        
        if (value == 0.0) {
            entries.removeIf(e -> e.row == row && e.col == col);
            return;
        }
        
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.row == row && e.col == col) {
                entries.set(i, new Entry(row, col, value));
                return;
            }
        }
        
        entries.add(new Entry(row, col, value));
    }
    
    /**
     * Gets the value at the specified position.
     * 
     * @param row row index
     * @param col column index
     * @return value at the specified position (0.0 if not present)
     * @throws IndexOutOfBoundsException if index is out of bounds
     */
    public double getEntry(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException(
                String.format("Index out of bounds: row=%d (max=%d), col=%d (max=%d)", 
                    row, rows - 1, col, cols - 1));
        }
        
        for (Entry e : entries) {
            if (e.row == row && e.col == col) {
                return e.value;
            }
        }
        return 0.0;
    }
    
    /**
     * Computes matrix-vector product (y = A * x).
     * 
     * @param x input vector (length must match number of columns)
     * @return result vector (length equals number of rows)
     * @throws IllegalArgumentException if vector length does not match number of columns
     */
    public double[] operate(double[] x) {
        if (x.length != cols) {
            throw new IllegalArgumentException(
                String.format("Vector length mismatch: expected %d, got %d", cols, x.length));
        }
        
        double[] result = new double[rows];
        
        for (Entry e : entries) {
            result[e.row] += e.value * x[e.col];
        }
        
        return result;
    }
    
    /**
     * Computes transpose matrix-vector product (y = A^T * x).
     * 
     * @param x input vector (length must match number of rows)
     * @return result vector (length equals number of columns)
     * @throws IllegalArgumentException if vector length does not match number of rows
     */
    public double[] transposeOperate(double[] x) {
        if (x.length != rows) {
            throw new IllegalArgumentException(
                String.format("Vector length mismatch: expected %d, got %d", rows, x.length));
        }
        
        double[] result = new double[cols];
        
        for (Entry e : entries) {
            result[e.col] += e.value * x[e.row];
        }
        
        return result;
    }
    
    /**
     * Returns the number of non-zero elements.
     * 
     * @return number of non-zero elements
     */
    public int getNonZeroCount() {
        return entries.size();
    }
    
    /**
     * Returns the sparsity (0.0 to 1.0, where 1.0 is most sparse).
     * 
     * @return sparsity (ratio of non-zero elements)
     */
    public double getSparsity() {
        if (rows == 0 || cols == 0) {
            return 1.0;
        }
        return 1.0 - (double) entries.size() / (rows * cols);
    }
}

