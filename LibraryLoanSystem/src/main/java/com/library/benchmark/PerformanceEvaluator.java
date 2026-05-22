package com.library.benchmark;

import com.library.connection.ConnectionManager;

import java.sql.*;
import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PerformanceEvaluator
 * Benchmarks four JDBC strategies and produces a structured report.
 *
 *  Suite 1 – Insert Strategy  : individual executeUpdate vs addBatch/executeBatch
 *  Suite 2 – Query Strategy   : full-table scan vs indexed lookup on Loans
 *  Suite 3 – Statement Type   : Statement (string concat) vs PreparedStatement
 *  Suite 4 – Tx Granularity   : per-operation commit vs batched commit (100 ops)
 */
public class PerformanceEvaluator {

    private static final int RUNS       = 5;   // runs per test
    private static final int WARMUP_MS  = 200; // warm-up duration
    private static final int SMALL_N    = 100;
    private static final int LARGE_N    = 1000;

    private final ConnectionManager cm;
    private final List<BenchmarkResult> results = new ArrayList<>();

    public PerformanceEvaluator(ConnectionManager cm) {
        this.cm = cm;
    }

    // ------------------------------------------------------------------ //
    //  Public entry point
    // ------------------------------------------------------------------ //

    public void runAllBenchmarks() {
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║        PERFORMANCE EVALUATION STARTING               ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        try {
            preparePerformanceTable();

            System.out.println("[ Warming up JVM + Derby buffer cache... ]");
            warmUp();

            System.out.println("\n[ Suite 1: Insert Strategy ]");
            benchmarkInsert(SMALL_N);
            benchmarkInsert(LARGE_N);

            System.out.println("\n[ Suite 2: Query Strategy ]");
            benchmarkQuery();

            System.out.println("\n[ Suite 3: Statement vs PreparedStatement ]");
            benchmarkStatementType();

            System.out.println("\n[ Suite 4: Transaction Granularity ]");
            benchmarkTransactionGranularity();

            printReport();
            saveReportToCSV();

        } catch (Exception e) {
            System.out.println("[BENCH] Benchmark error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanupPerformanceTable();
        }
    }

    // ------------------------------------------------------------------ //
    //  Suite 1: Batch vs Individual Inserts
    // ------------------------------------------------------------------ //

    private void benchmarkInsert(int n) throws SQLException {
        // Individual inserts
        double[] individualTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearPerfTable();
            long start = System.nanoTime();
            Connection conn = cm.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO PerfTest(Val, Note) VALUES (?,?)")) {
                for (int i = 0; i < n; i++) {
                    ps.setInt(1, i); ps.setString(2, "individual");
                    ps.executeUpdate();
                }
                conn.commit();
            }
            individualTimes[r] = nanoToMs(System.nanoTime() - start);
            System.out.printf("  Individual(%d) run %d: %.2f ms%n", n, r+1, individualTimes[r]);
        }

        // Batch inserts
        double[] batchTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearPerfTable();
            long start = System.nanoTime();
            Connection conn = cm.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO PerfTest(Val, Note) VALUES (?,?)")) {
                for (int i = 0; i < n; i++) {
                    ps.setInt(1, i); ps.setString(2, "batch");
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
            batchTimes[r] = nanoToMs(System.nanoTime() - start);
            System.out.printf("  Batch(%d)      run %d: %.2f ms%n", n, r+1, batchTimes[r]);
        }

        double indMean = mean(individualTimes), indStd = std(individualTimes);
        double batMean = mean(batchTimes),      batStd = std(batchTimes);
        double speedup = indMean / batMean;

        results.add(new BenchmarkResult(
            "Insert – Individual", n, indMean, indStd, n / (indMean/1000.0),
            String.format("Batch is %.1fx faster for %d records", speedup, n)));
        results.add(new BenchmarkResult(
            "Insert – Batch", n, batMean, batStd, n / (batMean/1000.0),
            "Reduces round-trips; single commit"));
    }

    // ------------------------------------------------------------------ //
    //  Suite 2: Full-table scan vs indexed lookup
    // ------------------------------------------------------------------ //

    private void benchmarkQuery() throws SQLException {
        // Ensure there are enough rows in Loans for a meaningful scan
        seedLoansForBenchmark();

        // Full table scan (no WHERE clause, unindexed column)
        double[] scanTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            long start = System.nanoTime();
            try (Statement st = cm.getConnection().createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM Loans")) {
                int cnt = 0; while (rs.next()) cnt++;
            }
            scanTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        // Indexed lookup (MemberID has an index)
        double[] indexedTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            long start = System.nanoTime();
            try (PreparedStatement ps = cm.getConnection().prepareStatement(
                    "SELECT * FROM Loans WHERE MemberID = ?")) {
                ps.setInt(1, 1);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()); }
            }
            indexedTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        // Indexed lookup via DueDate index
        double[] dueTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            long start = System.nanoTime();
            try (PreparedStatement ps = cm.getConnection().prepareStatement(
                    "SELECT * FROM Loans WHERE DueDate < CURRENT_DATE AND Status = 'ACTIVE'")) {
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()); }
            }
            dueTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        results.add(new BenchmarkResult("Query – Full Scan", -1,
            mean(scanTimes), std(scanTimes), 1000.0/mean(scanTimes),
            "Scans all rows; expensive as table grows"));
        results.add(new BenchmarkResult("Query – Indexed (MemberID)", -1,
            mean(indexedTimes), std(indexedTimes), 1000.0/mean(indexedTimes),
            "B-tree index on MemberID avoids full scan"));
        results.add(new BenchmarkResult("Query – Indexed (DueDate)", -1,
            mean(dueTimes), std(dueTimes), 1000.0/mean(dueTimes),
            "Range query on DueDate index"));
    }

    // ------------------------------------------------------------------ //
    //  Suite 3: Statement vs PreparedStatement
    // ------------------------------------------------------------------ //

    private void benchmarkStatementType() throws SQLException {
        int n = 200;

        // Statement (string concatenation) — vulnerable to SQL injection, slow
        double[] stmtTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearPerfTable();
            long start = System.nanoTime();
            Connection conn = cm.getConnection();
            for (int i = 0; i < n; i++) {
                String sql = "INSERT INTO PerfTest(Val, Note) VALUES (" + i + ", 'stmt')";
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(sql);
                }
            }
            conn.commit();
            stmtTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        // PreparedStatement (compiled once, executed many times)
        double[] prepTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearPerfTable();
            long start = System.nanoTime();
            Connection conn = cm.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO PerfTest(Val, Note) VALUES (?,?)")) {
                for (int i = 0; i < n; i++) {
                    ps.setInt(1, i); ps.setString(2, "prep");
                    ps.executeUpdate();
                }
                conn.commit();
            }
            prepTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        results.add(new BenchmarkResult("Statement (string concat)", n,
            mean(stmtTimes), std(stmtTimes), n / (mean(stmtTimes)/1000.0),
            "Re-parsed every execution; SQL injection risk"));
        results.add(new BenchmarkResult("PreparedStatement", n,
            mean(prepTimes), std(prepTimes), n / (mean(prepTimes)/1000.0),
            "Compiled once; safer and faster on repeated use"));
    }

    // ------------------------------------------------------------------ //
    //  Suite 4: Per-operation commit vs batched commit
    // ------------------------------------------------------------------ //

    private void benchmarkTransactionGranularity() throws SQLException {
        int n = 100;

        // Per-operation commit
        double[] perOpTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearPerfTable();
            long start = System.nanoTime();
            Connection conn = cm.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO PerfTest(Val, Note) VALUES (?,?)")) {
                for (int i = 0; i < n; i++) {
                    ps.setInt(1, i); ps.setString(2, "per-op");
                    ps.executeUpdate();
                    conn.commit();  // commit every single row
                }
            }
            perOpTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        // Batched commit (all 100 in one transaction)
        double[] batchedTimes = new double[RUNS];
        for (int r = 0; r < RUNS; r++) {
            clearPerfTable();
            long start = System.nanoTime();
            Connection conn = cm.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO PerfTest(Val, Note) VALUES (?,?)")) {
                for (int i = 0; i < n; i++) {
                    ps.setInt(1, i); ps.setString(2, "batched");
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();  // single commit for all 100
            }
            batchedTimes[r] = nanoToMs(System.nanoTime() - start);
        }

        double speedup = mean(perOpTimes) / mean(batchedTimes);
        results.add(new BenchmarkResult("Tx – Per-Op Commit", n,
            mean(perOpTimes), std(perOpTimes), n / (mean(perOpTimes)/1000.0),
            "100 fsync calls; safe but very slow"));
        results.add(new BenchmarkResult("Tx – Batched Commit", n,
            mean(batchedTimes), std(batchedTimes), n / (mean(batchedTimes)/1000.0),
            String.format("Single fsync; %.1fx faster; atomic for all 100 ops", speedup)));
    }

    // ------------------------------------------------------------------ //
    //  Report printing
    // ------------------------------------------------------------------ //

    private void printReport() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("\n\n╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf( "║  PERFORMANCE REPORT  —  %s  (%d runs per test, JVM warm-up applied)%n", ts, RUNS);
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║ %-38s │ %8s │ %12s │ %8s │ %14s │ %-30s ║%n",
            "Operation", "Records", "Avg Time(ms)", "Std Dev", "Throughput/s", "Observation");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");
        for (BenchmarkResult r : results) {
            String rec = r.records < 0 ? "N/A" : String.valueOf(r.records);
            System.out.printf("║ %-38s │ %8s │ %12.2f │ %8.2f │ %14.1f │ %-30s ║%n",
                r.operationName, rec, r.avgTimeMs, r.stdDev, r.throughput,
                truncate(r.observation, 30));
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝");
    }

    private void saveReportToCSV() {
        String filename = "output/performance_report.csv";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("Operation,Records,AvgTimeMs,StdDevMs,ThroughputPerSec,Observation");
            for (BenchmarkResult r : results) {
                pw.printf("%s,%d,%.2f,%.2f,%.1f,\"%s\"%n",
                    r.operationName, r.records, r.avgTimeMs, r.stdDev, r.throughput, r.observation);
            }
            System.out.println("\n[BENCH] CSV report saved to: " + filename);
        } catch (IOException e) {
            System.out.println("[BENCH] Could not save CSV: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void warmUp() throws SQLException, InterruptedException {
        long deadline = System.currentTimeMillis() + WARMUP_MS;
        while (System.currentTimeMillis() < deadline) {
            try (Statement st = cm.getConnection().createStatement()) {
                st.executeQuery("SELECT COUNT(*) FROM Books").close();
            }
        }
        System.gc();  // optionally clear JVM heap before benchmarks
    }

    private void preparePerformanceTable() throws SQLException {
        DatabaseMetaData meta = cm.getConnection().getMetaData();
        try (ResultSet rs = meta.getTables(null, "APP", "PERFTEST", new String[]{"TABLE"})) {
            if (!rs.next()) {
                try (Statement st = cm.getConnection().createStatement()) {
                    st.execute("CREATE TABLE PerfTest (ID INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, Val INT, Note VARCHAR(50))");
                    cm.getConnection().commit();
                }
            }
        }
    }

    private void cleanupPerformanceTable() {
        try (Statement st = cm.getConnection().createStatement()) {
            st.execute("DROP TABLE PerfTest");
            cm.getConnection().commit();
        } catch (SQLException ignored) {}
    }

    private void clearPerfTable() throws SQLException {
        try (Statement st = cm.getConnection().createStatement()) {
            st.execute("DELETE FROM PerfTest");
            cm.getConnection().commit();
        }
    }

    private void seedLoansForBenchmark() throws SQLException {
        // Add a few hundred synthetic loan rows if Loans is sparse
        try (Statement st = cm.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Loans")) {
            rs.next();
            if (rs.getInt(1) >= 50) return;
        }
        String sql = "INSERT INTO Loans(BookID, MemberID, LoanDate, DueDate, Status) VALUES (?,?,CURRENT_DATE,?,?)";
        try (PreparedStatement ps = cm.getConnection().prepareStatement(sql)) {
            Random rng = new Random(42);
            String[] statuses = {"ACTIVE","RETURNED","OVERDUE"};
            for (int i = 0; i < 200; i++) {
                int bookId   = (rng.nextInt(10)) + 1;
                int memberId = (rng.nextInt(5)) + 1;
                String due   = "2024-0" + (rng.nextInt(9)+1) + "-15";
                ps.setInt(1, bookId); ps.setInt(2, memberId);
                ps.setString(3, due); ps.setString(4, statuses[rng.nextInt(3)]);
                ps.addBatch();
            }
            ps.executeBatch();
            cm.getConnection().commit();
        }
    }

    private double nanoToMs(long nanos) { return nanos / 1_000_000.0; }

    private double mean(double[] arr) {
        double sum = 0; for (double v : arr) sum += v; return sum / arr.length;
    }

    private double std(double[] arr) {
        double m = mean(arr), s = 0;
        for (double v : arr) s += (v-m)*(v-m);
        return Math.sqrt(s / arr.length);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max-1) + "…";
    }

    // ------------------------------------------------------------------ //
    //  Result DTO
    // ------------------------------------------------------------------ //

    private static class BenchmarkResult {
        final String operationName;
        final int    records;
        final double avgTimeMs;
        final double stdDev;
        final double throughput;
        final String observation;

        BenchmarkResult(String op, int rec, double avg, double std, double tp, String obs) {
            this.operationName = op; this.records    = rec;
            this.avgTimeMs     = avg; this.stdDev    = std;
            this.throughput    = tp; this.observation = obs;
        }
    }
}
