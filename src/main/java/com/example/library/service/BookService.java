package com.example.library.service;

import com.example.library.dto.BookRequest;
import com.example.library.entity.Book;
import com.example.library.entity.Borrower;
import com.example.library.entity.Loan;
import com.example.library.exception.BookNotAvailableException;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.exception.NotFoundException;
import com.example.library.observability.LibraryMetrics;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowerRepository;
import com.example.library.repository.LoanRepository;
import com.example.library.util.IdGenerator;
import io.opentelemetry.api.trace.Span;
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
        if (bookRepository.existsById(id)) {
            throw new DuplicateResourceException("Book already exists: " + id);
        }
        Book book = new Book(id, request.title(), request.author(), request.yearOfPublication(), request.edition());
        return bookRepository.save(book);
    }

    public Book updateBook(String id, BookRequest request) {
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

            Book book = bookRepository.findById(bookId)
                    .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
            Borrower borrower = borrowerRepository.findById(borrowerId)
                    .orElseThrow(() -> new NotFoundException("Borrower not found: " + borrowerId));

            if (!book.isAvailable()) {
                throw new BookNotAvailableException("Book is not available: " + bookId);
            }

            book.setAvailable(false);
            bookRepository.save(book);

            Loan loan = loanRepository.save(new Loan(book, borrower, Instant.now()));
            metrics.recordBorrow(bookId, borrowerId);

            return loan;
        } finally {
            span.end();
        }
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}
