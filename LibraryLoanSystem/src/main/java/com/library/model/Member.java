package com.library.model;

public class Member {
    private int memberId;
    private String name;
    private String email;
    private String phone;
    private int activeLoans;
    private String joinDate;

    public Member() {}

    public Member(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.activeLoans = 0;
    }

    public int getMemberId()           { return memberId; }
    public void setMemberId(int id)    { this.memberId = id; }
    public String getName()            { return name; }
    public void setName(String name)   { this.name = name; }
    public String getEmail()           { return email; }
    public void setEmail(String e)     { this.email = e; }
    public String getPhone()           { return phone; }
    public void setPhone(String p)     { this.phone = p; }
    public int getActiveLoans()        { return activeLoans; }
    public void setActiveLoans(int a)  { this.activeLoans = a; }
    public String getJoinDate()        { return joinDate; }
    public void setJoinDate(String d)  { this.joinDate = d; }

    @Override
    public String toString() {
        return String.format("Member[id=%d, name=%s, email=%s, activeLoans=%d]",
                memberId, name, email, activeLoans);
    }
}
