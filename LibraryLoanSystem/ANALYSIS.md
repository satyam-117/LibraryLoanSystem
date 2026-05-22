# Analysis: Transaction Behavior & JDBC Performance Findings

## 1. Transaction Management and Data Integrity

### How ACID Transactions Preserve Integrity

The `processLoan()` operation spans four SQL statements:
1. SELECT — verify book availability
2. UPDATE Books — decrement AvailableCopies
3. INSERT Loans — create loan record
4. UPDATE Members — increment ActiveLoans

Without explicit transactions, a JVM crash or constraint violation between steps 2 and 4 would leave the database in an inconsistent state: a book copy counted as "loaned out" with no corresponding loan record, or a loan record with no matching member update.

By disabling auto-commit and wrapping all four steps in a single transaction, the system guarantees **atomicity**: either all four succeed and are committed, or none of them persist. The `rollback()` call in the catch block undoes any partial work, as verified by the rollback demo (Option 6) where BookID copies remain unchanged after a failed loan attempt with an invalid MemberID.

### Savepoint Strategy

A named savepoint `sp_book_updated` is set after step 2 (book decrement) but before the loan INSERT. This allows granular rollback to undo only steps 3–4 if the loan INSERT fails, while theoretically preserving the book status update. In practice, since full data integrity requires all four steps to succeed together, the implementation performs a full rollback to ensure consistency — the savepoint serves as a diagnostic anchor and could support partial retry logic in a future extension.

### Isolation and Constraint Violations

Derby's default isolation level (READ COMMITTED) prevents dirty reads. Constraint tests in the demo show:
- **UNIQUE constraint** on `Members.Email` — inserting a duplicate rolls back cleanly
- **CHECK constraint** on `ActiveLoans >= 0` — prevents negative loan counts
- **FOREIGN KEY** constraints on Loans — invalid BookID or MemberID causes immediate rollback

Post-rollback queries confirm the database state matches the pre-transaction state exactly.

---

## 2. JDBC Performance Findings

### Suite 1 — Batch vs Individual Inserts

Batch inserts (`addBatch()` + `executeBatch()`) are significantly faster than individual `executeUpdate()` calls, with the performance gap widening as record count increases. For 1,000 rows, batching reduces round-trips to the Derby engine from 1,000 to 1, and the single commit means only one log flush to disk.

**Key insight:** Individual inserts trigger one parse-plan-execute-commit cycle per row. Batching amortizes parsing and reduces I/O by combining multiple row insertions into a single network/IPC message to the Derby engine.

### Suite 2 — Indexed vs Full-Table Scan

Indexed lookups on `MemberID` and `DueDate` outperform full-table scans. Derby uses a B-tree index for equality and range predicates; without an index, Derby must read every row in the `Loans` table (heap scan). The difference is small on a small dataset but grows proportionally with table size.

**Trade-off:** Indexes speed up reads but add overhead on writes (index maintenance). For a loans table that is read-heavy after initial population, the indexes on `MemberID`, `DueDate`, and `ReturnDate` are well-justified.

### Suite 3 — Statement vs PreparedStatement

`PreparedStatement` outperforms `Statement` on repeated executions. Derby parses and compiles the query plan once when the statement is prepared; subsequent executions reuse the cached plan. `Statement` re-parses the SQL string every time, wasting CPU.

**Security note:** Beyond performance, `Statement` with string concatenation is vulnerable to SQL injection. `PreparedStatement` with parameter binding is the only correct approach for user-supplied data.

### Suite 4 — Transaction Granularity

Per-operation commits (one `commit()` per INSERT) are dramatically slower than a single batched commit for 100 operations. Each commit forces Derby to flush its transaction log to disk (fsync), making per-operation commit I/O-bound.

**Trade-off:** Per-operation commits maximise durability (each row is safe immediately after commit) but sacrifice throughput. Batched commits improve performance by orders of magnitude at the cost of losing all work if the process crashes mid-batch. For a library loan system where individual loan records are business-critical, a hybrid approach — commit every 10–50 operations — balances safety and performance.

---

## 3. Derby-Specific Observations

- **Buffer cache warm-up:** Derby caches data pages in memory. The first few queries after startup are slower (cold cache). The benchmark warm-up phase (200 ms of dummy queries) stabilises results by ensuring the cache is populated before timing begins.
- **Embedded mode advantage:** No network serialisation overhead; all IPC is in-process JNI. This makes embedded Derby faster than client-server databases for single-JVM workloads but unsuitable for multi-process concurrent access.
- **Shutdown:** The `shutdown=true` URL parameter releases Derby's file lock on the database folder. Omitting this leaves a lock file that can prevent the next startup.
