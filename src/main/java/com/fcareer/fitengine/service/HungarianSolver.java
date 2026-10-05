package com.fcareer.fitengine.service;

import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class HungarianSolver {

    private static final int UNASSIGNED = -1;

    /**
     * Satır = oyuncu, sütun = slot. Toplam skoru maksimize eden atamayı döner:
     * result[row] = atanan sütun ya da -1. Dikdörtgen matrisler (oyuncu > slot / oyuncu < slot) desteklenir.
     */
    public int[] maximize(double[][] scores) {
        int rows = scores.length;
        if (rows == 0) {
            return new int[0];
        }
        int cols = scores[0].length;
        int n = Math.max(rows, cols);
        double max = 0;
        for (double[] row : scores) {
            for (double value : row) {
                max = Math.max(max, value);
            }
        }
        double[][] cost = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                boolean real = i < rows && j < cols;
                cost[i][j] = real ? max - scores[i][j] : max;
            }
        }
        int[] assignment = minimize(cost);
        int[] result = new int[rows];
        Arrays.fill(result, UNASSIGNED);
        for (int i = 0; i < rows; i++) {
            if (assignment[i] < cols) {
                result[i] = assignment[i];
            }
        }
        return result;
    }

    private int[] minimize(double[][] cost) {
        int n = cost.length;
        double[] u = new double[n + 1];
        double[] v = new double[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[n + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            boolean[] used = new boolean[n + 1];
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (!used[j]) {
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] rowToCol = new int[n];
        for (int j = 1; j <= n; j++) {
            rowToCol[p[j] - 1] = j - 1;
        }
        return rowToCol;
    }
}
