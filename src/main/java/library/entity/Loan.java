package library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * The unique constraint enforces "one borrower may hold at most one copy of a
 * given book" in the database itself. The service checks this before inserting,
 * but a check-then-insert is only as good as the surrounding lock - the
 * constraint is what makes the rule true regardless of how the row got there.
 */
@Entity
@Table(
        name = "loans",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_loans_book_borrower",
                columnNames = {"book_id", "borrower_id"}
        )
)
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @Column(nullable = false)
    private Instant borrowedAt;

    protected Loan() {
    }

    public Loan(Book book, Borrower borrower, Instant borrowedAt) {
        this.book = book;
        this.borrower = borrower;
        this.borrowedAt = borrowedAt;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public Borrower getBorrower() {
        return borrower;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }
}
