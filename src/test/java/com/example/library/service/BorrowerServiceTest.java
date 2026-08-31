package com.example.library.service;

import com.example.library.dto.BorrowerRequest;
import com.example.library.entity.Book;
import com.example.library.entity.Borrower;
import com.example.library.entity.Loan;
import com.example.library.exception.DuplicateResourceException;
import com.example.library.exception.NotFoundException;
import com.example.library.repository.BorrowerRepository;
import com.example.library.repository.LoanRepository;
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
class BorrowerServiceTest {

    @Mock
    private BorrowerRepository borrowerRepository;
    @Mock
    private LoanRepository loanRepository;

    private BorrowerService borrowerService;

    @BeforeEach
    void setUp() {
        borrowerService = new BorrowerService(borrowerRepository, loanRepository);
    }

    @Test
    void createBorrower_generatesIdFromNameAndDateOfBirth() {
        BorrowerRequest request = new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(borrowerRepository.existsById("jane_doe_19900512")).thenReturn(false);
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Borrower result = borrowerService.createBorrower(request);

        assertThat(result.getId()).isEqualTo("jane_doe_19900512");
        assertThat(result.getName()).isEqualTo("Jane Doe");
        assertThat(result.getAddress()).isEqualTo("123 Main St");
    }

    @Test
    void createBorrower_throwsWhenGeneratedIdAlreadyExists() {
        BorrowerRequest request = new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(borrowerRepository.existsById("jane_doe_19900512")).thenReturn(true);

        assertThatThrownBy(() -> borrowerService.createBorrower(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void listBorrowers_returnsAllBorrowersFromRepository() {
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(borrowerRepository.findAll()).thenReturn(List.of(borrower));

        List<Borrower> result = borrowerService.listBorrowers();

        assertThat(result).containsExactly(borrower);
    }

    @Test
    void getBorrower_returnsBorrowerWhenFound() {
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(borrowerRepository.findById("jane_doe_19900512")).thenReturn(Optional.of(borrower));

        Borrower result = borrowerService.getBorrower("jane_doe_19900512");

        assertThat(result).isEqualTo(borrower);
    }

    @Test
    void getBorrower_throwsWhenNotFound() {
        when(borrowerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> borrowerService.getBorrower("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateBorrower_updatesMutableFieldsButKeepsId() {
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        when(borrowerRepository.findById(borrower.getId())).thenReturn(Optional.of(borrower));
        when(borrowerRepository.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Borrower result = borrowerService.updateBorrower(borrower.getId(),
                new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "456 Oak Ave"));

        assertThat(result.getId()).isEqualTo("jane_doe_19900512");
        assertThat(result.getAddress()).isEqualTo("456 Oak Ave");
    }

    @Test
    void updateBorrower_throwsWhenNotFound() {
        when(borrowerRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> borrowerService.updateBorrower("missing",
                new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteBorrower_removesBorrowerAndTheirLoans() {
        when(borrowerRepository.existsById("jane_doe_19900512")).thenReturn(true);

        borrowerService.deleteBorrower("jane_doe_19900512");

        verify(loanRepository).deleteByBorrowerId("jane_doe_19900512");
        verify(borrowerRepository).deleteById("jane_doe_19900512");
    }

    @Test
    void deleteBorrower_throwsWhenNotFound() {
        when(borrowerRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> borrowerService.deleteBorrower("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getBorrowedBooks_returnsBooksFromLoans() {
        Book book = new Book("robert_c_martin_clean_code_2008_1st", "Clean Code", "Robert C. Martin", 2008, "1st");
        Borrower borrower = new Borrower("jane_doe_19900512", "Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        Loan loan = new Loan(book, borrower, Instant.now());
        when(borrowerRepository.existsById("jane_doe_19900512")).thenReturn(true);
        when(loanRepository.findByBorrowerId("jane_doe_19900512")).thenReturn(List.of(loan));

        List<Book> result = borrowerService.getBorrowedBooks("jane_doe_19900512");

        assertThat(result).containsExactly(book);
    }

    @Test
    void getBorrowedBooks_throwsWhenBorrowerNotFound() {
        when(borrowerRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> borrowerService.getBorrowedBooks("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
