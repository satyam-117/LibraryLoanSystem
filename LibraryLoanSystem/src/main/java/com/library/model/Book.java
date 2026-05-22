package com.library.model;

public class Book {
    private int bookId;
    private String isbn;
    private String title;
    private String author;
    private String genre;
    private boolean available;
    private int totalCopies;
    private int availableCopies;

    public Book() {}

    public Book(String isbn, String title, String author, String genre, int totalCopies) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
        this.available = true;
    }

    public int getBookId()                  { return bookId; }
    public void setBookId(int id)           { this.bookId = id; }
    public String getIsbn()                 { return isbn; }
    public void setIsbn(String isbn)        { this.isbn = isbn; }
    public String getTitle()                { return title; }
    public void setTitle(String t)          { this.title = t; }
    public String getAuthor()               { return author; }
    public void setAuthor(String a)         { this.author = a; }
    public String getGenre()                { return genre; }
    public void setGenre(String g)          { this.genre = g; }
    public boolean isAvailable()            { return available; }
    public void setAvailable(boolean av)    { this.available = av; }
    public int getTotalCopies()             { return totalCopies; }
    public void setTotalCopies(int t)       { this.totalCopies = t; }
    public int getAvailableCopies()         { return availableCopies; }
    public void setAvailableCopies(int a)   { this.availableCopies = a; }

    @Override
    public String toString() {
        return String.format("Book[id=%d, isbn=%s, title='%s', author=%s, available=%d/%d]",
                bookId, isbn, title, author, availableCopies, totalCopies);
    }
}
