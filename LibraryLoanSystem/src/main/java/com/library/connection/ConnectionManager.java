package com.library.connection;

import java.sql.*;
import java.util.logging.*;

/**
 * ConnectionManager
 * Handles embedded Apache Derby database initialization, connection lifecycle,
 * schema creation, seed data, and graceful shutdown.
 */
public class ConnectionManager {

    private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());

    // Embedded Derby URL — creates DB folder "librarydb" in current working dir
    private static final String DB_URL     = "jdbc:derby:librarydb;create=true";
    private static final String SHUTDOWN_URL = "jdbc:derby:librarydb;shutdown=true";
    private static final String DRIVER     = "org.apache.derby.jdbc.EmbeddedDriver";

    private static ConnectionManager instance;
    private Connection connection;

    // ------------------------------------------------------------------ //
    //  Singleton
    // ------------------------------------------------------------------ //

    private ConnectionManager() {}

    public static synchronized ConnectionManager getInstance() {
        if (instance == null) instance = new ConnectionManager();
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /** Returns a single shared connection (auto-commit OFF). */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    /** Creates schema + seed data if not already present. */
    public void initialize() {
        try {
            Class.forName(DRIVER);
            connect();
            createSchema();
            seedData();
            System.out.println("[DB] Library database initialized successfully.");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Derby JDBC driver not found. Ensure derby.jar is on classpath.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Database initialization failed: " + e.getMessage(), e);
        }
    }

    /** Cleanly shuts down the embedded Derby engine. */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}

        try {
            DriverManager.getConnection(SHUTDOWN_URL);
        } catch (SQLException e) {
            // Derby always throws SQLState 08006 on clean shutdown — that is expected
            if ("08006".equals(e.getSQLState()) || "XJ015".equals(e.getSQLState())) {
                System.out.println("[DB] Derby shut down cleanly.");
            } else {
                LOG.warning("Unexpected shutdown error: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    private void connect() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        connection.setAutoCommit(false);   // All transactions are explicit
    }

    private void createSchema() throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();

        // --- Members ---
        if (!tableExists(meta, "MEMBERS")) {
            try (Statement st = connection.createStatement()) {
                st.execute(
                    "CREATE TABLE Members (" +
                    "  MemberID    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY," +
                    "  Name        VARCHAR(100) NOT NULL," +
                    "  Email       VARCHAR(150) NOT NULL UNIQUE," +
                    "  Phone       VARCHAR(20)," +
                    "  ActiveLoans INT DEFAULT 0 CHECK (ActiveLoans >= 0)," +
                    "  JoinDate    DATE DEFAULT CURRENT_DATE" +
                    ")"
                );
                connection.commit();
                System.out.println("[DB] Table Members created.");
            }
        }

        // --- Books ---
        if (!tableExists(meta, "BOOKS")) {
            try (Statement st = connection.createStatement()) {
                st.execute(
                    "CREATE TABLE Books (" +
                    "  BookID          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY," +
                    "  ISBN            VARCHAR(20) NOT NULL UNIQUE," +
                    "  Title           VARCHAR(200) NOT NULL," +
                    "  Author          VARCHAR(150) NOT NULL," +
                    "  Genre           VARCHAR(50)," +
                    "  TotalCopies     INT DEFAULT 1 CHECK (TotalCopies > 0)," +
                    "  AvailableCopies INT DEFAULT 1 CHECK (AvailableCopies >= 0)" +
                    ")"
                );
                // Index on ISBN for fast lookup
                st.execute("CREATE INDEX idx_books_isbn ON Books(ISBN)");
                connection.commit();
                System.out.println("[DB] Table Books created (+ index on ISBN).");
            }
        }

        // --- Loans ---
        if (!tableExists(meta, "LOANS")) {
            try (Statement st = connection.createStatement()) {
                st.execute(
                    "CREATE TABLE Loans (" +
                    "  LoanID     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY," +
                    "  BookID     INT NOT NULL REFERENCES Books(BookID)," +
                    "  MemberID   INT NOT NULL REFERENCES Members(MemberID)," +
                    "  LoanDate   DATE DEFAULT CURRENT_DATE," +
                    "  DueDate    DATE NOT NULL," +
                    "  ReturnDate DATE," +
                    "  Status     VARCHAR(10) DEFAULT 'ACTIVE' CHECK (Status IN ('ACTIVE','RETURNED','OVERDUE'))" +
                    ")"
                );
                st.execute("CREATE INDEX idx_loans_member   ON Loans(MemberID)");
                st.execute("CREATE INDEX idx_loans_return   ON Loans(ReturnDate)");
                st.execute("CREATE INDEX idx_loans_due      ON Loans(DueDate)");
                connection.commit();
                System.out.println("[DB] Table Loans created (+ indexes on MemberID, ReturnDate, DueDate).");
            }
        }
    }

    private void seedData() throws SQLException {
        // Only seed if empty
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM Members")) {
            rs.next();
            if (rs.getInt(1) > 0) return;
        }

        System.out.println("[DB] Seeding initial data...");

        // Members
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Members(Name, Email, Phone) VALUES (?,?,?)")) {
            String[][] members = {
                {"Alice Johnson","alice@example.com","9876543210"},
                {"Bob Smith","bob@example.com","9123456780"},
                {"Carol White","carol@example.com","9988776655"},
                {"David Lee","david@example.com","9001122334"},
                {"Eva Martinez","eva@example.com","9567890123"}
            };
            for (String[] m : members) {
                ps.setString(1, m[0]); ps.setString(2, m[1]); ps.setString(3, m[2]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // Books
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO Books(ISBN, Title, Author, Genre, TotalCopies, AvailableCopies) VALUES (?,?,?,?,?,?)")) {
            Object[][] books = {
                {"978-0-06-112008-4","To Kill a Mockingbird","Harper Lee","Fiction",3,3},
                {"978-0-7432-7356-5","1984","George Orwell","Dystopian",2,2},
                {"978-0-14-028329-7","Of Mice and Men","John Steinbeck","Fiction",4,4},
                {"978-0-06-093546-9","To Kill a Mockingbird 2","Harper Lee","Fiction",1,1},
                {"978-0-7432-7357-2","Animal Farm","George Orwell","Fable",3,3},
                {"978-0-14-028330-3","East of Eden","John Steinbeck","Fiction",2,2},
                {"978-0-06-112009-1","Brave New World","Aldous Huxley","Dystopian",2,2},
                {"978-0-7432-7358-9","Fahrenheit 451","Ray Bradbury","Sci-Fi",3,3},
                {"978-0-14-028331-0","The Grapes of Wrath","John Steinbeck","Fiction",1,1},
                {"978-0-06-093547-6","Catch-22","Joseph Heller","Satire",2,2}
            };
            for (Object[] b : books) {
                ps.setString(1,(String)b[0]); ps.setString(2,(String)b[1]);
                ps.setString(3,(String)b[2]); ps.setString(4,(String)b[3]);
                ps.setInt(5,(int)b[4]);       ps.setInt(6,(int)b[5]);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        connection.commit();
        System.out.println("[DB] Seed data inserted (5 members, 10 books).");
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, "APP", tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
