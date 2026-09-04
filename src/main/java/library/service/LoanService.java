package library.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;

    public LoanService(LoanRepository loanRepository, BookRepository bookRepository, EntityManager entityManager) {
        this.loanRepository = loanRepository;
        this.bookRepository = bookRepository;
        this.entityManager = entityManager;
    }

    public List<Loan> listLoans() {
        return loanRepository.findAll();
    }

    @Transactional
    public void returnBook(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found: " + loanId));

        // The same lost-update hazard as borrowing, in the opposite direction:
        // two concurrent returns of different loans for the same book would both
        // read the same count and both write count + 1, so a returned copy
        // silently vanishes.
        //
        // A locking repository lookup would not fix it. Loading the loan has
        // already pulled its book into the persistence context (@ManyToOne is
        // EAGER), so a second query takes the row lock but still hands back that
        // first-level cache instance, carrying the count read before the lock
        // was held. refresh() re-reads the row from the database under the lock,
        // so the increment applies to current committed state.
        //
        // flush() must come first: refresh() discards pending in-memory changes
        // instead of writing them, and unlike a JPQL query it does not trigger
        // an auto-flush. Without it, an earlier change to this book in the same
        // transaction would be dropped and re-read at its stale value.
        Book book = loan.getBook();
        entityManager.flush();
        entityManager.refresh(book, LockModeType.PESSIMISTIC_WRITE);

        book.incrementQuantity();
        bookRepository.save(book);
        loanRepository.delete(loan);
    }
}
