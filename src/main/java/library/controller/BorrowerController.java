package library.controller;

import library.dto.BookResponse;
import library.dto.BorrowerRequest;
import library.dto.BorrowerResponse;
import library.entity.Borrower;
import library.service.BorrowerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/borrowers")
public class BorrowerController {

    private final BorrowerService borrowerService;

    public BorrowerController(BorrowerService borrowerService) {
        this.borrowerService = borrowerService;
    }

    @GetMapping
    public List<BorrowerResponse> listBorrowers() {
        return borrowerService.listBorrowers().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BorrowerResponse createBorrower(@Valid @RequestBody BorrowerRequest request) {
        return toResponse(borrowerService.createBorrower(request));
    }

    @GetMapping("/{id}")
    public BorrowerResponse getBorrower(@PathVariable String id) {
        return toResponse(borrowerService.getBorrower(id));
    }

    @PutMapping("/{id}")
    public BorrowerResponse updateBorrower(@PathVariable String id, @Valid @RequestBody BorrowerRequest request) {
        return toResponse(borrowerService.updateBorrower(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBorrower(@PathVariable String id) {
        borrowerService.deleteBorrower(id);
    }

    @GetMapping("/{id}/books")
    public List<BookResponse> getBorrowedBooks(@PathVariable String id) {
        return borrowerService.getBorrowedBooks(id).stream()
                .map(book -> new BookResponse(book.getId(), book.getTitle(), book.getAuthor(),
                        book.getYearOfPublication(), book.getEdition(), book.getQuantity()))
                .toList();
    }

    private BorrowerResponse toResponse(Borrower borrower) {
        return new BorrowerResponse(borrower.getId(), borrower.getName(), borrower.getDateOfBirth(), borrower.getAddress());
    }
}
