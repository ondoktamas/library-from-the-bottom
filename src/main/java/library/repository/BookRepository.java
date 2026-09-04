package library.repository;

import jakarta.persistence.LockModeType;
import library.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, String> {

    /**
     * Loads a book with a row-level write lock (SELECT ... FOR UPDATE) held until
     * the surrounding transaction commits. Every path that changes {@code quantity}
     * must go through this rather than {@link #findById}, otherwise concurrent
     * borrows read the same stale count and each write their own decrement back -
     * lost updates that hand out more copies than exist.
     *
     * <p>Callers must be {@code @Transactional}; a lock acquired outside a
     * transaction would be released immediately and guarantee nothing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Book> findWithLockById(String id);

    /**
     * Looks a book up by identity rather than by its (lossy, creation-time) slug
     * id. This is what decides whether an add merges into an existing book or
     * creates a new one, and unlike an id lookup it stays correct after the book
     * has been edited.
     */
    Optional<Book> findByNormalizedAuthorAndNormalizedTitleAndYearOfPublicationAndNormalizedEdition(
            String normalizedAuthor, String normalizedTitle, Integer yearOfPublication, String normalizedEdition);
}
