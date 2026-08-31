package com.ondok.library.controller;

import com.ondok.library.dto.LoanResponse;
import com.ondok.library.entity.Loan;
import com.ondok.library.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    public List<LoanResponse> listLoans() {
        return loanService.listLoans().stream().map(this::toResponse).toList();
    }

    @DeleteMapping("/{loanId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void returnBook(@PathVariable Long loanId) {
        loanService.returnBook(loanId);
    }

    private LoanResponse toResponse(Loan loan) {
        return new LoanResponse(loan.getId(), loan.getBook().getId(), loan.getBook().getTitle(),
                loan.getBorrower().getId(), loan.getBorrower().getName(), loan.getBorrowedAt());
    }
}
