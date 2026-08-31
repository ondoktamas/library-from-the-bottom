package com.ondok.library.controller;

import com.ondok.library.dto.BookRequest;
import com.ondok.library.dto.BookResponse;
import com.ondok.library.dto.BorrowRequest;
import com.ondok.library.dto.BorrowResponse;
import com.ondok.library.entity.Book;
import com.ondok.library.entity.Loan;
import com.ondok.library.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public List<BookResponse> listBooks(@RequestParam(required = false) String id,
                                         @RequestParam(required = false) String title,
                                         @RequestParam(required = false) String author) {
        return bookService.listBooks(id, title, author).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookResponse addBook(@Valid @RequestBody BookRequest request) {
        return toResponse(bookService.addBook(request));
    }

    @PutMapping("/{bookId}")
    public BookResponse updateBook(@PathVariable String bookId, @Valid @RequestBody BookRequest request) {
        return toResponse(bookService.updateBook(bookId, request));
    }

    @DeleteMapping("/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable String bookId) {
        bookService.deleteBook(bookId);
    }

    @PostMapping("/{bookId}/borrow")
    @ResponseStatus(HttpStatus.CREATED)
    public BorrowResponse borrowBook(@PathVariable String bookId, @Valid @RequestBody BorrowRequest request) {
        Loan loan = bookService.borrowBook(bookId, request.borrowerId());
        return new BorrowResponse(loan.getId(), loan.getBook().getId(), loan.getBorrower().getId(), loan.getBorrowedAt());
    }

    private BookResponse toResponse(Book book) {
        return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(),
                book.getYearOfPublication(), book.getEdition(), book.isAvailable());
    }
}
