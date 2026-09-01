package library.service;

import library.entity.Book;
import library.entity.Borrower;
import library.entity.Loan;
import library.exception.NotFoundException;
import library.repository.BookRepository;
import library.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;
    @Mock
    private BookRepository bookRepository;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, bookRepository);
    }

    @Test
    void listLoans_returnsAllLoansFromRepository() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 1);
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        Loan loan = new Loan(book, borrower, Instant.now());
        when(loanRepository.findAll()).thenReturn(List.of(loan));

        List<Loan> result = loanService.listLoans();

        assertThat(result).containsExactly(loan);
    }

    @Test
    void returnBook_incrementsQuantityAndDeletesLoan() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st", 0);
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        Loan loan = new Loan(book, borrower, Instant.now());
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        loanService.returnBook(1L);

        assertThat(book.getQuantity()).isEqualTo(1);
        verify(bookRepository).save(book);
        verify(loanRepository).delete(loan);
    }

    @Test
    void returnBook_throwsWhenLoanNotFound() {
        when(loanRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.returnBook(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
