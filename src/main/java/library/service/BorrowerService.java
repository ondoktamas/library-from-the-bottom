package library.service;

import library.dto.BorrowerRequest;
import library.entity.Book;
import library.entity.Borrower;
import library.entity.Loan;
import library.exception.DuplicateResourceException;
import library.exception.NotFoundException;
import library.repository.BorrowerRepository;
import library.repository.LoanRepository;
import library.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BorrowerService {

    private final BorrowerRepository borrowerRepository;
    private final LoanRepository loanRepository;

    public BorrowerService(BorrowerRepository borrowerRepository, LoanRepository loanRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanRepository = loanRepository;
    }

    public List<Borrower> listBorrowers() {
        return borrowerRepository.findAll();
    }

    public Borrower createBorrower(BorrowerRequest request) {
        String id = IdGenerator.borrowerId(request.name(), request.dateOfBirth());
        if (borrowerRepository.existsById(id)) {
            throw new DuplicateResourceException("Borrower already exists: " + id);
        }
        Borrower borrower = new Borrower(id, request.name(), request.dateOfBirth(), request.address());
        return borrowerRepository.save(borrower);
    }

    public Borrower getBorrower(String id) {
        return borrowerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Borrower not found: " + id));
    }

    public Borrower updateBorrower(String id, BorrowerRequest request) {
        Borrower borrower = borrowerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Borrower not found: " + id));
        borrower.update(request.name(), request.dateOfBirth(), request.address());
        return borrowerRepository.save(borrower);
    }

    @Transactional
    public void deleteBorrower(String id) {
        if (!borrowerRepository.existsById(id)) {
            throw new NotFoundException("Borrower not found: " + id);
        }
        loanRepository.deleteByBorrowerId(id);
        borrowerRepository.deleteById(id);
    }

    public List<Book> getBorrowedBooks(String borrowerId) {
        if (!borrowerRepository.existsById(borrowerId)) {
            throw new NotFoundException("Borrower not found: " + borrowerId);
        }
        return loanRepository.findByBorrowerId(borrowerId).stream()
                .map(Loan::getBook)
                .toList();
    }
}
