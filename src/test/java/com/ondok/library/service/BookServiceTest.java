package com.ondok.library.service;

import com.ondok.library.dto.BookRequest;
import com.ondok.library.entity.Book;
import com.ondok.library.entity.Borrower;
import com.ondok.library.entity.Loan;
import com.ondok.library.exception.BookNotAvailableException;
import com.ondok.library.exception.DuplicateResourceException;
import com.ondok.library.exception.NotFoundException;
import com.ondok.library.observability.LibraryMetrics;
import com.ondok.library.repository.BookRepository;
import com.ondok.library.repository.BorrowerRepository;
import com.ondok.library.repository.LoanRepository;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BorrowerRepository borrowerRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private LibraryMetrics metrics;

    // A real (no-op) Tracer keeps this a true unit test: no mocking of the
    // OpenTelemetry API's fluent span builder is required.
    private final Tracer tracer = OpenTelemetry.noop().getTracer("test");

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository, borrowerRepository, loanRepository, tracer, metrics);
    }

    @Test
    void addBook_generatesIdFromAuthorTitleYearAndEdition() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", 2008, "1st");
        when(bookRepository.existsById("robert_c_martin_clean_code_2008_1st")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book result = bookService.addBook(request);

        assertThat(result.getId()).isEqualTo("robert_c_martin_clean_code_2008_1st");
        assertThat(result.isAvailable()).isTrue();
    }

    @Test
    void addBook_throwsWhenGeneratedIdAlreadyExists() {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", 2008, "1st");
        when(bookRepository.existsById("robert_c_martin_clean_code_2008_1st")).thenReturn(true);

        assertThatThrownBy(() -> bookService.addBook(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateBook_updatesMutableFieldsButKeepsId() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book result = bookService.updateBook(book.getId(),
                new BookRequest("Clean Code (Revised)", "Robert C. Martin", 2009, "2nd"));

        assertThat(result.getId()).isEqualTo("robert_c_martin_clean_code_2008_1st");
        assertThat(result.getTitle()).isEqualTo("Clean Code (Revised)");
        assertThat(result.getYearOfPublication()).isEqualTo(2009);
        assertThat(result.getEdition()).isEqualTo("2nd");
    }

    @Test
    void updateBook_throwsWhenNotFound() {
        when(bookRepository.findById("missing_book")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.updateBook("missing_book",
                new BookRequest("Title", "Author", 2020, "1st")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteBook_removesBookAndItsLoans() {
        when(bookRepository.existsById("robert_c_martin_clean_code_2008_1st")).thenReturn(true);

        bookService.deleteBook("robert_c_martin_clean_code_2008_1st");

        verify(loanRepository).deleteByBookId("robert_c_martin_clean_code_2008_1st");
        verify(bookRepository).deleteById("robert_c_martin_clean_code_2008_1st");
    }

    @Test
    void deleteBook_throwsWhenNotFound() {
        when(bookRepository.existsById("missing_book")).thenReturn(false);

        assertThatThrownBy(() -> bookService.deleteBook("missing_book"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listBooks_filtersByTitleCaseInsensitively() {
        Book cleanCode = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        Book effectiveJava = new Book("joshua_bloch_effective_java_2018_3rd", "Effective Java", "Joshua Bloch", 2018, "3rd");
        when(bookRepository.findAll()).thenReturn(List.of(cleanCode, effectiveJava));

        List<Book> result = bookService.listBooks(null, "clean", null);

        assertThat(result).containsExactly(cleanCode);
    }

    @Test
    void listBooks_filtersByAuthorAndId() {
        Book cleanCode = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        Book effectiveJava = new Book("joshua_bloch_effective_java_2018_3rd", "Effective Java", "Joshua Bloch", 2018, "3rd");
        when(bookRepository.findAll()).thenReturn(List.of(cleanCode, effectiveJava));

        List<Book> byAuthor = bookService.listBooks(null, null, "bloch");
        List<Book> byId = bookService.listBooks("robert_c_martin_clean_code_2008_1st", null, null);

        assertThat(byAuthor).containsExactly(effectiveJava);
        assertThat(byId).containsExactly(cleanCode);
    }

    @Test
    void borrowBook_marksBookUnavailableAndCreatesLoan() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById(borrower.getId())).thenReturn(Optional.of(borrower));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = bookService.borrowBook(book.getId(), borrower.getId());

        assertThat(book.isAvailable()).isFalse();
        assertThat(loan.getBook()).isEqualTo(book);
        assertThat(loan.getBorrower()).isEqualTo(borrower);
        verify(metrics).recordBorrow(book.getId(), borrower.getId());
    }

    @Test
    void borrowBook_throwsWhenBookNotFound() {
        when(bookRepository.findById("missing_book")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.borrowBook("missing_book", "jane_doe_19900512"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void borrowBook_throwsWhenBorrowerNotFound() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById("missing_borrower")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.borrowBook(book.getId(), "missing_borrower"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void borrowBook_throwsWhenBookAlreadyBorrowed() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        book.setAvailable(false);
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById(borrower.getId())).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> bookService.borrowBook(book.getId(), borrower.getId()))
                .isInstanceOf(BookNotAvailableException.class);
    }
}
