package com.library.model;

public class Loan {
    private int loanId;
    private int bookId;
    private int memberId;
    private String loanDate;
    private String dueDate;
    private String returnDate;
    private String status; // ACTIVE, RETURNED, OVERDUE

    // For display joins
    private String bookTitle;
    private String memberName;

    public Loan() {}

    public Loan(int bookId, int memberId, String loanDate, String dueDate) {
        this.bookId = bookId;
        this.memberId = memberId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.status = "ACTIVE";
    }

    public int getLoanId()               { return loanId; }
    public void setLoanId(int id)        { this.loanId = id; }
    public int getBookId()               { return bookId; }
    public void setBookId(int b)         { this.bookId = b; }
    public int getMemberId()             { return memberId; }
    public void setMemberId(int m)       { this.memberId = m; }
    public String getLoanDate()          { return loanDate; }
    public void setLoanDate(String d)    { this.loanDate = d; }
    public String getDueDate()           { return dueDate; }
    public void setDueDate(String d)     { this.dueDate = d; }
    public String getReturnDate()        { return returnDate; }
    public void setReturnDate(String d)  { this.returnDate = d; }
    public String getStatus()            { return status; }
    public void setStatus(String s)      { this.status = s; }
    public String getBookTitle()         { return bookTitle; }
    public void setBookTitle(String t)   { this.bookTitle = t; }
    public String getMemberName()        { return memberName; }
    public void setMemberName(String n)  { this.memberName = n; }

    @Override
    public String toString() {
        return String.format("Loan[id=%d, book='%s', member='%s', due=%s, status=%s]",
                loanId, bookTitle, memberName, dueDate, status);
    }
}
