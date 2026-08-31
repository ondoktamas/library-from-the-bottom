package com.ondok.library.repository;

import com.ondok.library.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByBorrowerId(String borrowerId);

    void deleteByBookId(String bookId);

    void deleteByBorrowerId(String borrowerId);
}
