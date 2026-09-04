package library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import library.util.IdGenerator;

/**
 * The {@code id} is a readable slug fixed at creation time so that URLs and loan
 * references never break. It is deliberately <em>not</em> the book's identity:
 * it is lossy (different books can slug alike) and it goes stale as soon as the
 * book is edited.
 *
 * <p>Identity lives in the normalised author/title/year/edition columns instead.
 * They are recomputed on every edit and carry a unique constraint, so "is this
 * the same book?" has one answer that stays correct after an update and cannot
 * be fooled by punctuation.
 */
@Entity
@Table(
        name = "books",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_books_natural_key",
                columnNames = {"normalized_author", "normalized_title", "year_of_publication", "normalized_edition"}
        )
)
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
    private Integer quantity;

    @Column(name = "normalized_title", nullable = false)
    private String normalizedTitle;

    @Column(name = "normalized_author", nullable = false)
    private String normalizedAuthor;

    @Column(name = "normalized_edition", nullable = false)
    private String normalizedEdition;

    protected Book() {
    }

    public Book(String id, String title, String author, Integer yearOfPublication, String edition, Integer quantity) {
        this.id = id;
        this.quantity = quantity;
        applyDescriptiveFields(title, author, yearOfPublication, edition);
    }

    /**
     * Updates the descriptive fields only. {@code quantity} is intentionally not
     * a parameter: it is availability, not a description, and is maintained by
     * {@link #increaseQuantity}, {@link #decrementQuantity} and
     * {@link #incrementQuantity} as copies are added, borrowed and returned.
     * Overwriting it from an edit loses track of copies that are out on loan.
     */
    public void update(String title, String author, Integer yearOfPublication, String edition) {
        applyDescriptiveFields(title, author, yearOfPublication, edition);
    }

    /**
     * Single place where the descriptive fields are written, so the normalised
     * copies can never drift out of step with them. An edit that skipped this
     * would leave the natural key describing the book's <em>old</em> title,
     * which is what used to let a renamed book be added a second time as a
     * duplicate.
     */
    private void applyDescriptiveFields(String title, String author, Integer yearOfPublication, String edition) {
        this.title = title;
        this.author = author;
        this.yearOfPublication = yearOfPublication;
        this.edition = edition;
        this.normalizedTitle = IdGenerator.normalize(title);
        this.normalizedAuthor = IdGenerator.normalize(author);
        this.normalizedEdition = IdGenerator.normalize(edition);
    }

    public boolean hasAvailableCopies() {
        return quantity > 0;
    }

    public void decrementQuantity() {
        quantity--;
    }

    public void incrementQuantity() {
        quantity++;
    }

    public void increaseQuantity(int amount) {
        quantity += amount;
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

    public Integer getQuantity() {
        return quantity;
    }
}
