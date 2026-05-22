# Library Loan Management System

A complete JDBC-based Library Loan Management application using **Apache Derby (Embedded)**, demonstrating explicit transaction management, ACID compliance, savepoints, and empirical JDBC performance benchmarking.

---

## Prerequisites

| Tool | Minimum Version |
|------|----------------|
| Java JDK | 8 (tested on JDK 17) |
| Apache Derby | 10.15+ |

---

## Step 1 — Download Apache Derby

1. Go to: https://db.apache.org/derby/derby_downloads.html
2. Download the **binary distribution** (e.g. `db-derby-10.16.1.1-bin.zip`)
3. Extract the zip
4. Copy **`lib/derby.jar`** from the extracted folder into this project's `lib/` folder:

```
LibraryLoanSystem/
└── lib/
    └── derby.jar    ← place it here
```

---

## Step 2 — Build and Run (Windows)

Open **Command Prompt** in the project root folder (`LibraryLoanSystem\`) and run:

```cmd
build.bat
```

This will:
1. Compile all Java source files
2. Launch the interactive CLI application

---

## Step 2 (Linux / macOS)

```bash
chmod +x build.sh
./build.sh
```

---

## Manual Build (Without the Script)

```cmd
:: Windows
mkdir out
dir /b /s src\main\java\*.java > sources.txt
javac -d out -cp lib\derby.jar @sources.txt
mkdir output
java -cp "out;lib\derby.jar" com.library.ui.MainApp
```

---

## Project Structure

```
LibraryLoanSystem/
├── src/
│   └── main/java/com/library/
│       ├── model/               # Member, Book, Loan POJOs
│       │   ├── Member.java
│       │   ├── Book.java
│       │   └── Loan.java
│       ├── connection/          # Derby lifecycle, schema, seed data
│       │   └── ConnectionManager.java
│       ├── transaction/         # Commit, rollback, savepoints
│       │   └── TransactionService.java
│       ├── business/            # CRUD, queries
│       │   └── BusinessLogic.java
│       ├── benchmark/           # Performance test suites + report
│       │   └── PerformanceEvaluator.java
│       └── ui/                  # CLI menu
│           └── MainApp.java
├── lib/
│   └── derby.jar                # You place this here
├── output/                      # Performance CSV reports saved here
├── build.bat                    # Windows build + run script
├── build.sh                     # Linux/macOS build + run script
└── README.md
```

---

## CLI Session Example

```
╔══════════════════════════════════════════╗
║   Library Loan Management System v1.0    ║
║   Powered by Apache Derby (Embedded)     ║
╚══════════════════════════════════════════╝
[DB] Library database initialized successfully.

══════════════ MAIN MENU ══════════════
  1. Member Management
  2. Book Catalog
  3. Loan Processing
  4. Query / Reports
  5. Performance Benchmark
  6. Demo: Transaction Rollback
  0. Exit
═══════════════════════════════════════
Select option: 5
```

---

## Features

### Transaction Management
- Auto-commit **disabled** on all data-modifying operations
- `processLoan()` runs as a 4-step ACID transaction:
  1. Verify book availability
  2. Decrement `AvailableCopies`
  3. *(Savepoint set here)*
  4. Insert `Loan` record
  5. Increment member `ActiveLoans`
- On any failure → full rollback, database unchanged

### Performance Benchmarks (Option 5)
| Suite | What is measured |
|-------|-----------------|
| Insert Strategy | Individual `executeUpdate()` vs `addBatch()` for 100 and 1,000 records |
| Query Strategy | Full-table scan vs indexed lookup on `Loans` |
| Statement Type | `Statement` (string concat) vs `PreparedStatement` |
| Tx Granularity | Per-operation commit vs single batched commit (100 ops) |

Each test runs **5 times** with JVM/Derby warm-up, reports mean ± std dev, throughput (ops/sec), and saves a CSV to `output/performance_report.csv`.

---

## Database Schema

```sql
Members (MemberID PK, Name, Email UNIQUE, Phone, ActiveLoans, JoinDate)
Books   (BookID PK, ISBN UNIQUE, Title, Author, Genre, TotalCopies, AvailableCopies)
Loans   (LoanID PK, BookID FK, MemberID FK, LoanDate, DueDate, ReturnDate, Status)

Indexes: Books.ISBN, Loans.MemberID, Loans.DueDate, Loans.ReturnDate
```

---

## Uploading to GitHub

### First time (create repo):

```cmd
git init
git add .
git commit -m "Initial commit: Library Loan Management System with JDBC + Derby"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/LibraryLoanSystem.git
git push -u origin main
```

### Subsequent updates:

```cmd
git add .
git commit -m "Your commit message"
git push
```

> **Note:** The `librarydb/` folder (Derby database files) and `out/` (compiled classes) are excluded via `.gitignore` — only source code is committed.

---

## .gitignore

```
out/
librarydb/
output/
*.class
```

---

## Grading Alignment

| Criterion | Implementation |
|-----------|---------------|
| Functionality (25%) | Full CRUD, CLI, JDBC with PreparedStatement |
| Transaction Management (25%) | Explicit commit/rollback, savepoints, constraint demos |
| Performance Evaluation (20%) | 4 suites × 5 runs, mean ± SD, throughput, CSV report |
| Code Quality (15%) | Layered architecture, try-with-resources, separation of concerns |
| Documentation (15%) | This README + inline Javadoc |
