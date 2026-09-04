package library.service;

import library.dto.BookRequest;
import library.dto.BookUpdateRequest;
import library.entity.Book;
import library.entity.Borrower;
import library.entity.Loan;
import library.exception.BookNotAvailableException;
import library.exception.DuplicateLoanException;
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

    public Book addBook(BookRequest request) {
        String id = IdGenerator.bookId(request.author(), request.title(), request.yearOfPublication(), request.edition());
        Book book = bookRepository.findById(id)
                .map(existing -> {
                    existing.increaseQuantity(request.quantity());
                    return existing;
                })
                .orElseGet(() -> new Book(id, request.title(), request.author(), request.yearOfPublication(), request.edition(), request.quantity()));
        return bookRepository.save(book);
    }

    @Transactional
    public Book updateBook(String id, BookUpdateRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Book not found: " + id));
        book.update(request.title(), request.author(), request.yearOfPublication(), request.edition());
        return bookRepository.save(book);
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
