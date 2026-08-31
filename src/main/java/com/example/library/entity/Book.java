package com.example.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(name = "year_of_publication", nullable = false)
    private Integer yearOfPublication;

    @Column(nullable = false)
    private String edition;

    @Column(nullable = false)
    private boolean available;

    protected Book() {
    }

    public Book(String id, String title, String author, Integer yearOfPublication, String edition) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.yearOfPublication = yearOfPublication;
        this.edition = edition;
        this.available = true;
    }

    public void update(String title, String author, Integer yearOfPublication, String edition) {
        this.title = title;
        this.author = author;
        this.yearOfPublication = yearOfPublication;
        this.edition = edition;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Integer getYearOfPublication() {
        return yearOfPublication;
    }

    public String getEdition() {
        return edition;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
