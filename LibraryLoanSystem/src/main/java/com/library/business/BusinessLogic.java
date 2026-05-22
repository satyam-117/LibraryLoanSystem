package com.library.business;

import com.library.connection.ConnectionManager;
import com.library.model.*;
import com.library.transaction.TransactionService;

import java.sql.*;
import java.util.*;

/**
 * BusinessLogic
 * CRUD operations for Members, Books, and Loans.
 * All writes use PreparedStatement + explicit transactions.
 */
public class BusinessLogic {

    private final ConnectionManager cm;
    private final TransactionService txn;

    public BusinessLogic(ConnectionManager cm, TransactionService txn) {
        this.cm  = cm;
        this.txn = txn;
    }

    // ==================================================================
    //  MEMBER operations
    // ==================================================================

    public int registerMember(String name, String email, String phone) {
        String sql = "INSERT INTO Members(Name, Email, Phone) VALUES (?,?,?)";
        try (PreparedStatement ps = cm.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, email); ps.setString(3, phone);
            ps.executeUpdate();
            cm.getConnection().commit();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    System.out.println("[BIZ] Member registered with ID: " + id);
                    return id;
                }
            }
        } catch (SQLException e) {
            txn.rollback();
            System.out.println("[BIZ] Register member failed: " + e.getMessage());
        }
        return -1;
    }

    public List<Member> getAllMembers() {
        List<Member> list = new ArrayList<>();
        String sql = "SELECT * FROM Members ORDER BY MemberID";
        try (Statement st = cm.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Member m = new Member();
                m.setMemberId(rs.getInt("MemberID"));
                m.setName(rs.getString("Name"));
                m.setEmail(rs.getString("Email"));
                m.setPhone(rs.getString("Phone"));
                m.setActiveLoans(rs.getInt("ActiveLoans"));
                list.add(m);
            }
        } catch (SQLException e) {
            System.out.println("[BIZ] getAllMembers failed: " + e.getMessage());
        }
        return list;
    }

    public Optional<Member> getMemberById(int id) {
        String sql = "SELECT * FROM Members WHERE MemberID = ?";
        try (PreparedStatement ps = cm.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Member m = new Member();
                    m.setMemberId(rs.getInt("MemberID"));
                    m.setName(rs.getString("Name"));
                    m.setEmail(rs.getString("Email"));
                    m.setPhone(rs.getString("Phone"));
                    m.setActiveLoans(rs.getInt("ActiveLoans"));
                    return Optional.of(m);
                }
            }
        } catch (SQLException e) {
            System.out.println("[BIZ] getMemberById failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ==================================================================
    //  BOOK operations
    // ==================================================================

    public int addBook(String isbn, String title, String author, String genre, int copies) {
        String sql = "INSERT INTO Books(ISBN, Title, Author, Genre, TotalCopies, AvailableCopies) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = cm.getConnection().prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, isbn); ps.setString(2, title); ps.setString(3, author);
            ps.setString(4, genre); ps.setInt(5, copies); ps.setInt(6, copies);
            ps.executeUpdate();
            cm.getConnection().commit();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    System.out.println("[BIZ] Book added with ID: " + id);
                    return id;
                }
            }
        } catch (SQLException e) {
            txn.rollback();
            System.out.println("[BIZ] Add book failed: " + e.getMessage());
        }
        return -1;
    }

    public List<Book> getAllBooks() {
        List<Book> list = new ArrayList<>();
        String sql = "SELECT * FROM Books ORDER BY BookID";
        try (Statement st = cm.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapBook(rs));
        } catch (SQLException e) {
            System.out.println("[BIZ] getAllBooks failed: " + e.getMessage());
        }
        return list;
    }

    public List<Book> getAvailableBooks() {
        List<Book> list = new ArrayList<>();
        String sql = "SELECT * FROM Books WHERE AvailableCopies > 0 ORDER BY Title";
        try (Statement st = cm.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapBook(rs));
        } catch (SQLException e) {
            System.out.println("[BIZ] getAvailableBooks failed: " + e.getMessage());
        }
        return list;
    }

    public Optional<Book> getBookById(int id) {
        try (PreparedStatement ps = cm.getConnection().prepareStatement(
                "SELECT * FROM Books WHERE BookID = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapBook(rs));
            }
        } catch (SQLException e) {
            System.out.println("[BIZ] getBookById failed: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ==================================================================
    //  LOAN operations  (high-level delegates to TransactionService)
    // ==================================================================

    public int processLoan(int bookId, int memberId, String dueDate) {
        return txn.processLoanTransaction(bookId, memberId, dueDate);
    }

    public boolean returnBook(int loanId) {
        return txn.processReturnTransaction(loanId);
    }

    public List<Loan> getActiveLoansByMember(int memberId) {
        List<Loan> list = new ArrayList<>();
        String sql =
            "SELECT l.LoanID, l.BookID, l.MemberID, l.LoanDate, l.DueDate, l.Status, " +
            "       b.Title AS BookTitle, m.Name AS MemberName " +
            "FROM Loans l " +
            "JOIN Books b   ON l.BookID   = b.BookID " +
            "JOIN Members m ON l.MemberID = m.MemberID " +
            "WHERE l.MemberID = ? AND l.Status = 'ACTIVE' " +
            "ORDER BY l.DueDate";
        try (PreparedStatement ps = cm.getConnection().prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapLoan(rs));
            }
        } catch (SQLException e) {
            System.out.println("[BIZ] getActiveLoansByMember failed: " + e.getMessage());
        }
        return list;
    }

    public List<Loan> getOverdueLoans() {
        List<Loan> list = new ArrayList<>();
        String sql =
            "SELECT l.LoanID, l.BookID, l.MemberID, l.LoanDate, l.DueDate, l.Status, " +
            "       b.Title AS BookTitle, m.Name AS MemberName " +
            "FROM Loans l " +
            "JOIN Books b   ON l.BookID   = b.BookID " +
            "JOIN Members m ON l.MemberID = m.MemberID " +
            "WHERE l.Status = 'ACTIVE' AND l.DueDate < CURRENT_DATE " +
            "ORDER BY l.DueDate";
        try (Statement st = cm.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapLoan(rs));
        } catch (SQLException e) {
            System.out.println("[BIZ] getOverdueLoans failed: " + e.getMessage());
        }
        return list;
    }

    public List<Loan> getAllActiveLoans() {
        List<Loan> list = new ArrayList<>();
        String sql =
            "SELECT l.LoanID, l.BookID, l.MemberID, l.LoanDate, l.DueDate, l.Status, " +
            "       b.Title AS BookTitle, m.Name AS MemberName " +
            "FROM Loans l " +
            "JOIN Books b   ON l.BookID   = b.BookID " +
            "JOIN Members m ON l.MemberID = m.MemberID " +
            "WHERE l.Status = 'ACTIVE' " +
            "ORDER BY l.LoanDate DESC";
        try (Statement st = cm.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapLoan(rs));
        } catch (SQLException e) {
            System.out.println("[BIZ] getAllActiveLoans failed: " + e.getMessage());
        }
        return list;
    }

    // ==================================================================
    //  Private mappers
    // ==================================================================

    private Book mapBook(ResultSet rs) throws SQLException {
        Book b = new Book();
        b.setBookId(rs.getInt("BookID"));
        b.setIsbn(rs.getString("ISBN"));
        b.setTitle(rs.getString("Title"));
        b.setAuthor(rs.getString("Author"));
        b.setGenre(rs.getString("Genre"));
        b.setTotalCopies(rs.getInt("TotalCopies"));
        b.setAvailableCopies(rs.getInt("AvailableCopies"));
        return b;
    }

    private Loan mapLoan(ResultSet rs) throws SQLException {
        Loan l = new Loan();
        l.setLoanId(rs.getInt("LoanID"));
        l.setBookId(rs.getInt("BookID"));
        l.setMemberId(rs.getInt("MemberID"));
        Date ld = rs.getDate("LoanDate");
        Date dd = rs.getDate("DueDate");
        l.setLoanDate(ld != null ? ld.toString() : "");
        l.setDueDate(dd != null ? dd.toString() : "");
        l.setStatus(rs.getString("Status"));
        l.setBookTitle(rs.getString("BookTitle"));
        l.setMemberName(rs.getString("MemberName"));
        return l;
    }
}
