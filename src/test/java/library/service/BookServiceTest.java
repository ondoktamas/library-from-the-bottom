package library.service;

import library.dto.BookRequest;
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
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", 2008, "1st", 3);
        when(bookRepository.findById("robert_c_martin_clean_code_2008_1st")).thenReturn(Optional.empty());
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book result = bookService.addBook(request);

        assertThat(result.getId()).isEqualTo("robert_c_martin_clean_code_2008_1st");
        assertThat(result.getQuantity()).isEqualTo(3);
    }

    @Test
    void addBook_whenGeneratedIdAlreadyExists_increasesQuantityInstead() {
        Book existing = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", 2008, "1st", 3);
        when(bookRepository.findById("robert_c_martin_clean_code_2008_1st")).thenReturn(Optional.of(existing));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book result = bookService.addBook(request);

        assertThat(result).isSameAs(existing);
        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    void updateBook_updatesMutableFieldsButKeepsId() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Book result = bookService.updateBook(book.getId(),
                new BookRequest("Clean Code (Revised)", "Robert C. Martin", 2009, "2nd", 5));

        assertThat(result.getId()).isEqualTo("robert_c_martin_clean_code_2008_1st");
        assertThat(result.getTitle()).isEqualTo("Clean Code (Revised)");
        assertThat(result.getYearOfPublication()).isEqualTo(2009);
        assertThat(result.getEdition()).isEqualTo("2nd");
        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    void updateBook_throwsWhenNotFound() {
        when(bookRepository.findById("missing_book")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.updateBook("missing_book",
                new BookRequest("Title", "Author", 2020, "1st", 1)))
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
        Book cleanCode = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        Book effectiveJava = new Book("joshua_bloch_effective_java_2018_3rd", "Effective Java", "Joshua Bloch", 2018, "3rd", 2);
        when(bookRepository.findAll()).thenReturn(List.of(cleanCode, effectiveJava));

        List<Book> result = bookService.listBooks(null, "clean", null);

        assertThat(result).containsExactly(cleanCode);
    }

    @Test
    void listBooks_filtersByAuthorAndId() {
        Book cleanCode = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        Book effectiveJava = new Book("joshua_bloch_effective_java_2018_3rd", "Effective Java", "Joshua Bloch", 2018, "3rd", 2);
        when(bookRepository.findAll()).thenReturn(List.of(cleanCode, effectiveJava));

        List<Book> byAuthor = bookService.listBooks(null, null, "bloch");
        List<Book> byId = bookService.listBooks("robert_c_martin_clean_code_2008_1st", null, null);

        assertThat(byAuthor).containsExactly(effectiveJava);
        assertThat(byId).containsExactly(cleanCode);
    }

    @Test
    void borrowBook_decrementsQuantityAndCreatesLoan() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById(borrower.getId())).thenReturn(Optional.of(borrower));
        when(loanRepository.existsByBookIdAndBorrowerId(book.getId(), borrower.getId())).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Loan loan = bookService.borrowBook(book.getId(), borrower.getId());

        assertThat(book.getQuantity()).isEqualTo(1);
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
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById("missing_borrower")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.borrowBook(book.getId(), "missing_borrower"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void borrowBook_throwsWhenQuantityIsZero() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 0);
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById(borrower.getId())).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> bookService.borrowBook(book.getId(), borrower.getId()))
                .isInstanceOf(BookNotAvailableException.class);
    }

    @Test
    void borrowBook_throwsWhenSameBorrowerAlreadyHasThisBook() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 2);
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(bookRepository.findById(book.getId())).thenReturn(Optional.of(book));
        when(borrowerRepository.findById(borrower.getId())).thenReturn(Optional.of(borrower));
        when(loanRepository.existsByBookIdAndBorrowerId(book.getId(), borrower.getId())).thenReturn(true);

        assertThatThrownBy(() -> bookService.borrowBook(book.getId(), borrower.getId()))
                .isInstanceOf(DuplicateLoanException.class);
    }
}
