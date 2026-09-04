package library.service;

import library.dto.BookRequest;
import library.dto.BookUpdateRequest;
import library.entity.Book;
import library.entity.Borrower;
import library.entity.Loan;
import library.exception.BookNotAvailableException;
import library.exception.DuplicateLoanException;
import library.exception.DuplicateResourceException;
import library.exception.InvalidRequestException;
import library.exception.NotFoundException;
import library.observability.LibraryMetrics;
import library.repository.BookRepository;
import library.repository.BorrowerRepository;
import library.repository.LoanRepository;
import library.util.IdGenerator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;
    private final Tracer tracer;
    private final LibraryMetrics metrics;

    public BookService(BookRepository bookRepository,
                        BorrowerRepository borrowerRepository,
                        LoanRepository loanRepository,
                        Tracer tracer,
                        LibraryMetrics metrics) {
        this.bookRepository = bookRepository;
        this.borrowerRepository = borrowerRepository;
        this.loanRepository = loanRepository;
        this.tracer = tracer;
        this.metrics = metrics;
    }

    public List<Book> listBooks(String id, String title, String author) {
        return bookRepository.findAll().stream()
                .filter(book -> id == null || book.getId().equals(id))
                .filter(book -> title == null || containsIgnoreCase(book.getTitle(), title))
                .filter(book -> author == null || containsIgnoreCase(book.getAuthor(), author))
                .toList();
    }

    @Transactional
    public Book addBook(BookRequest request) {
        requireUsableSlugs(request.title(), request.author(), request.edition());

        // Matched on identity, not on the slug id. Looking the id up instead
        // used to miss any book that had since been renamed, silently creating
        // a second row for a book the library already had.
        Book book = findByNaturalKey(request.title(), request.author(), request.yearOfPublication(), request.edition())
                .map(existing -> {
                    existing.increaseQuantity(request.quantity());
                    return existing;
                })
                .orElseGet(() -> new Book(
                        generateAvailableId(request.author(), request.title(), request.yearOfPublication(), request.edition()),
                        request.title(), request.author(), request.yearOfPublication(), request.edition(), request.quantity()));
        return bookRepository.save(book);
    }

    @Transactional
    public Book updateBook(String id, BookUpdateRequest request) {
        requireUsableSlugs(request.title(), request.author(), request.edition());

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Book not found: " + id));

        // An edit changes the book's identity, so it can collide with a book that
        // already exists. Rejecting is the only honest option: merging would
        // destroy one of the two rows and their loans.
        findByNaturalKey(request.title(), request.author(), request.yearOfPublication(), request.edition())
                .filter(clash -> !clash.getId().equals(book.getId()))
                .ifPresent(clash -> {
                    throw new DuplicateResourceException(
                            "Another book with the same author, title, year and edition already exists: " + clash.getId());
                });

        book.update(request.title(), request.author(), request.yearOfPublication(), request.edition());
        return bookRepository.save(book);
    }

    private Optional<Book> findByNaturalKey(String title, String author, Integer yearOfPublication, String edition) {
        return bookRepository.findByNormalizedAuthorAndNormalizedTitleAndYearOfPublicationAndNormalizedEdition(
                IdGenerator.normalize(author), IdGenerator.normalize(title), yearOfPublication, IdGenerator.normalize(edition));
    }

    /**
     * Slugs are lossy, so two different books can want the same id. The first one
     * to be added keeps the clean slug and later arrivals get a numeric suffix.
     * Previously the collision was resolved by treating the second book as the
     * first, merging its quantity in and discarding its title and author.
     */
    private String generateAvailableId(String author, String title, int yearOfPublication, String edition) {
        String baseId = IdGenerator.bookId(author, title, yearOfPublication, edition);
        String candidate = baseId;
        for (int suffix = 2; bookRepository.existsById(candidate); suffix++) {
            candidate = baseId + "_" + suffix;
        }
        return candidate;
    }

    private void requireUsableSlugs(String title, String author, String edition) {
        if (!IdGenerator.hasUsableSlug(title)) {
            throw new InvalidRequestException("title must contain at least one letter or digit");
        }
        if (!IdGenerator.hasUsableSlug(author)) {
            throw new InvalidRequestException("author must contain at least one letter or digit");
        }
        if (!IdGenerator.hasUsableSlug(edition)) {
            throw new InvalidRequestException("edition must contain at least one letter or digit");
        }
    }

    @Transactional
    public void deleteBook(String id) {
        if (!bookRepository.existsById(id)) {
            throw new NotFoundException("Book not found: " + id);
        }
        loanRepository.deleteByBookId(id);
        bookRepository.deleteById(id);
    }

    @Transactional
    public Loan borrowBook(String bookId, String borrowerId) {
        Span span = tracer.spanBuilder("borrow-book").startSpan();
        try {
            span.setAttribute("book.id", bookId);
            span.setAttribute("borrower.id", borrowerId);

            // Locked read: serialises concurrent borrows of this book so the
            // availability check below and the decrement that follows cannot be
            // interleaved by another request. Also closes the duplicate-loan
            // race, since a competing borrow for the same book can only reach
            // the check after this transaction has committed its loan.
            Book book = bookRepository.findWithLockById(bookId)
                    .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
            Borrower borrower = borrowerRepository.findById(borrowerId)
                    .orElseThrow(() -> new NotFoundException("Borrower not found: " + borrowerId));

            if (!book.hasAvailableCopies()) {
                throw new BookNotAvailableException("Book is not available: " + bookId);
            }
            if (loanRepository.existsByBookIdAndBorrowerId(bookId, borrowerId)) {
                throw new DuplicateLoanException("Borrower " + borrowerId + " already has an active loan for book " + bookId);
            }

            book.decrementQuantity();
            bookRepository.save(book);

            Loan loan = loanRepository.save(new Loan(book, borrower, Instant.now()));
            metrics.recordBorrow(bookId, borrowerId);

            if (loan.getId() != null) {
                span.setAttribute("loan.id", loan.getId());
            }
            span.setAttribute("book.quantityRemaining", book.getQuantity());

            return loan;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
