package com.library.ui;

import com.library.benchmark.PerformanceEvaluator;
import com.library.business.BusinessLogic;
import com.library.connection.ConnectionManager;
import com.library.model.*;
import com.library.transaction.TransactionService;

import java.time.LocalDate;
import java.util.*;

/**
 * MainApp
 * Console-driven Library Loan Management System.
 * Orchestrates all components and provides the interactive CLI.
 */
public class MainApp {

    private static final Scanner sc = new Scanner(System.in);

    private final ConnectionManager  cm;
    private final TransactionService txn;
    private final BusinessLogic      biz;
    private final PerformanceEvaluator perf;

    public MainApp() {
        cm   = ConnectionManager.getInstance();
        txn  = new TransactionService(cm);
        biz  = new BusinessLogic(cm, txn);
        perf = new PerformanceEvaluator(cm);
    }

    // ------------------------------------------------------------------ //
    //  Entry point
    // ------------------------------------------------------------------ //

    public static void main(String[] args) {
        new MainApp().run();
    }

    public void run() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   Library Loan Management System v1.0    ║");
        System.out.println("║   Powered by Apache Derby (Embedded)     ║");
        System.out.println("╚══════════════════════════════════════════╝");

        cm.initialize();

        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = readInt("Select option: ");
            switch (choice) {
                case 1  -> memberMenu();
                case 2  -> bookMenu();
                case 3  -> loanMenu();
                case 4  -> queryMenu();
                case 5  -> perf.runAllBenchmarks();
                case 6  -> demoTransactionRollback();
                case 0  -> running = false;
                default -> System.out.println("Invalid option. Try again.");
            }
        }

        System.out.println("\nShutting down...");
        cm.shutdown();
        System.out.println("Goodbye!");
    }

    // ------------------------------------------------------------------ //
    //  Menus
    // ------------------------------------------------------------------ //

    private void printMainMenu() {
        System.out.println("\n══════════════ MAIN MENU ══════════════");
        System.out.println("  1. Member Management");
        System.out.println("  2. Book Catalog");
        System.out.println("  3. Loan Processing");
        System.out.println("  4. Query / Reports");
        System.out.println("  5. Performance Benchmark");
        System.out.println("  6. Demo: Transaction Rollback");
        System.out.println("  0. Exit");
        System.out.println("═══════════════════════════════════════");
    }

    // ----- MEMBERS -----
    private void memberMenu() {
        System.out.println("\n--- Member Management ---");
        System.out.println("  1. Register new member");
        System.out.println("  2. List all members");
        System.out.println("  3. Find member by ID");
        System.out.println("  0. Back");
        int choice = readInt("Select: ");
        switch (choice) {
            case 1 -> {
                String name  = readString("Name: ");
                String email = readString("Email: ");
                String phone = readString("Phone: ");
                int id = biz.registerMember(name, email, phone);
                if (id > 0) System.out.println("✔ Registered with MemberID = " + id);
                else        System.out.println("✘ Registration failed (email may be duplicate).");
            }
            case 2 -> {
                List<Member> members = biz.getAllMembers();
                printHeader("%-5s %-25s %-30s %-15s %s", "ID","Name","Email","Phone","ActiveLoans");
                members.forEach(m -> System.out.printf("  %-5d %-25s %-30s %-15s %d%n",
                    m.getMemberId(), m.getName(), m.getEmail(), m.getPhone(), m.getActiveLoans()));
            }
            case 3 -> {
                int id = readInt("MemberID: ");
                biz.getMemberById(id).ifPresentOrElse(
                    m -> System.out.println("  " + m),
                    ()  -> System.out.println("  Member not found."));
            }
        }
    }

    // ----- BOOKS -----
    private void bookMenu() {
        System.out.println("\n--- Book Catalog ---");
        System.out.println("  1. Add new book");
        System.out.println("  2. List all books");
        System.out.println("  3. List available books");
        System.out.println("  0. Back");
        int choice = readInt("Select: ");
        switch (choice) {
            case 1 -> {
                String isbn   = readString("ISBN: ");
                String title  = readString("Title: ");
                String author = readString("Author: ");
                String genre  = readString("Genre: ");
                int    copies = readInt("Number of copies: ");
                int id = biz.addBook(isbn, title, author, genre, copies);
                if (id > 0) System.out.println("✔ Book added with BookID = " + id);
                else        System.out.println("✘ Add book failed (ISBN may be duplicate).");
            }
            case 2 -> printBooks(biz.getAllBooks());
            case 3 -> printBooks(biz.getAvailableBooks());
        }
    }

    // ----- LOANS -----
    private void loanMenu() {
        System.out.println("\n--- Loan Processing ---");
        System.out.println("  1. Issue a loan");
        System.out.println("  2. Return a book");
        System.out.println("  0. Back");
        int choice = readInt("Select: ");
        switch (choice) {
            case 1 -> {
                printBooks(biz.getAvailableBooks());
                int bookId   = readInt("BookID to loan: ");
                int memberId = readInt("MemberID: ");
                String due   = LocalDate.now().plusWeeks(2).toString();
                System.out.println("Due date: " + due);
                int loanId = biz.processLoan(bookId, memberId, due);
                if (loanId > 0) System.out.println("✔ Loan created, LoanID = " + loanId);
                else            System.out.println("✘ Loan failed.");
            }
            case 2 -> {
                int loanId = readInt("LoanID to return: ");
                boolean ok = biz.returnBook(loanId);
                System.out.println(ok ? "✔ Book returned successfully." : "✘ Return failed.");
            }
        }
    }

    // ----- QUERIES -----
    private void queryMenu() {
        System.out.println("\n--- Queries & Reports ---");
        System.out.println("  1. Active loans by member");
        System.out.println("  2. All active loans");
        System.out.println("  3. Overdue loans");
        System.out.println("  0. Back");
        int choice = readInt("Select: ");
        switch (choice) {
            case 1 -> {
                int memberId = readInt("MemberID: ");
                printLoans(biz.getActiveLoansByMember(memberId));
            }
            case 2 -> printLoans(biz.getAllActiveLoans());
            case 3 -> {
                List<Loan> overdue = biz.getOverdueLoans();
                if (overdue.isEmpty()) System.out.println("  No overdue loans.");
                else printLoans(overdue);
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Transaction rollback demo
    // ------------------------------------------------------------------ //

    private void demoTransactionRollback() {
        System.out.println("\n╔══ TRANSACTION ROLLBACK DEMO ═══════════════════════════════════╗");
        System.out.println("║  Attempting loan with an INVALID MemberID (999) to trigger    ║");
        System.out.println("║  a constraint violation and demonstrate full rollback.         ║");
        System.out.println("╚═════════════════════════════════════════════════════════════════╝");

        List<Book> available = biz.getAvailableBooks();
        if (available.isEmpty()) {
            System.out.println("No available books for demo.");
            return;
        }
        int bookId = available.get(0).getBookId();
        int copiesBefore = available.get(0).getAvailableCopies();
        System.out.println("Using BookID=" + bookId + ", available copies BEFORE: " + copiesBefore);

        int result = biz.processLoan(bookId, 999, "2025-12-31");

        int copiesAfter = biz.getBookById(bookId).map(Book::getAvailableCopies).orElse(-1);
        System.out.println("Loan result (should be -1): " + result);
        System.out.println("Available copies AFTER rollback (should be " + copiesBefore + "): " + copiesAfter);
        if (copiesBefore == copiesAfter) {
            System.out.println("✔ ROLLBACK VERIFIED — database state preserved.");
        } else {
            System.out.println("✘ WARNING: Copies mismatch, rollback may have failed.");
        }

        System.out.println("\n--- Demonstrating duplicate email constraint ---");
        List<com.library.model.Member> members = biz.getAllMembers();
        if (!members.isEmpty()) {
            String existingEmail = members.get(0).getEmail();
            System.out.println("Trying to register member with existing email: " + existingEmail);
            int id = biz.registerMember("Duplicate User", existingEmail, "0000000000");
            System.out.println("Result (should be -1): " + id);
            if (id == -1) System.out.println("✔ UNIQUE constraint enforced, rollback confirmed.");
        }
    }

    // ------------------------------------------------------------------ //
    //  Display helpers
    // ------------------------------------------------------------------ //

    private void printBooks(List<Book> books) {
        if (books.isEmpty()) { System.out.println("  No books found."); return; }
        printHeader("%-5s %-15s %-35s %-20s %-12s %s",
            "ID","ISBN","Title","Author","Genre","Avail/Total");
        books.forEach(b -> System.out.printf("  %-5d %-15s %-35s %-20s %-12s %d/%d%n",
            b.getBookId(), b.getIsbn(), truncate(b.getTitle(), 33),
            truncate(b.getAuthor(), 18), b.getGenre(),
            b.getAvailableCopies(), b.getTotalCopies()));
    }

    private void printLoans(List<Loan> loans) {
        if (loans.isEmpty()) { System.out.println("  No loans found."); return; }
        printHeader("%-7s %-30s %-20s %-12s %-12s %s",
            "LoanID","Book","Member","LoanDate","DueDate","Status");
        loans.forEach(l -> System.out.printf("  %-7d %-30s %-20s %-12s %-12s %s%n",
            l.getLoanId(), truncate(l.getBookTitle(), 28),
            truncate(l.getMemberName(), 18),
            l.getLoanDate(), l.getDueDate(), l.getStatus()));
    }

    private void printHeader(String fmt, Object... cols) {
        String line = String.format(fmt, cols);
        System.out.println("\n  " + line);
        System.out.println("  " + "─".repeat(Math.min(line.length(), 110)));
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max-1) + "…";
    }

    // ------------------------------------------------------------------ //
    //  Input helpers
    // ------------------------------------------------------------------ //

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(sc.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("  Please enter a number."); }
        }
    }

    private String readString(String prompt) {
        System.out.print(prompt);
        return sc.nextLine().trim();
    }
}
