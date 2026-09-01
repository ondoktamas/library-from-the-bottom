package library.service;

import library.entity.Book;
import library.entity.Loan;
import library.exception.NotFoundException;
import library.repository.BookRepository;
import library.repository.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final BookRepository bookRepository;

    public LoanService(LoanRepository loanRepository, BookRepository bookRepository) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
    }

    public List<Loan> listLoans() {
        return loanRepository.findAll();
    }

    @Transactional
    public void returnBook(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found: " + loanId));
        Book book = loan.getBook();
        book.incrementQuantity();
        bookRepository.save(book);
        loanRepository.delete(loan);
    }
}
