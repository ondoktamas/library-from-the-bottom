package library.entity;

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
    private Integer quantity;

    protected Book() {
    }

    public Book(String id, String title, String author, Integer yearOfPublication, String edition, Integer quantity) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.yearOfPublication = yearOfPublication;
        this.edition = edition;
        this.quantity = quantity;
    }

    /**
     * Updates the descriptive fields only. {@code quantity} is intentionally not
     * a parameter: it is availability, not a description, and is maintained by
     * {@link #increaseQuantity}, {@link #decrementQuantity} and
     * {@link #incrementQuantity} as copies are added, borrowed and returned.
     * Overwriting it from an edit loses track of copies that are out on loan.
     */
    public void update(String title, String author, Integer yearOfPublication, String edition) {
        this.title = title;
        this.author = author;
        this.yearOfPublication = yearOfPublication;
        this.edition = edition;
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
