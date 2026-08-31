package com.ondok.library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondok.library.dto.BookRequest;
import com.ondok.library.dto.BorrowRequest;
import com.ondok.library.dto.BorrowerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full Spring context + real MockMvc dispatch + real H2 database.
 * {@code @Transactional} rolls each test's data back afterward, so tests
 * stay independent without manual cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.sql.init.mode=never")
class BookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void addBook_thenListBooks_returnsCreatedBookWithGeneratedId() throws Exception {
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", 2008, "1st");

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("robert_c_martin_clean_code_2008_1st"))
                .andExpect(jsonPath("$.available").value(true));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='robert_c_martin_clean_code_2008_1st')]").exists());
    }

    @Test
    void addBook_withMissingFields_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"title":"","author":"","yearOfPublication":null,"edition":""}""";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withDuplicateGeneratedId_returnsConflict() throws Exception {
        BookRequest request = new BookRequest("Dune", "Frank Herbert", 1965, "1st");

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listBooks_filtersByTitleAuthorAndId() throws Exception {
        createBook("Effective Java", "Joshua Bloch", 2018, "3rd");
        createBook("Java Concurrency in Practice", "Brian Goetz", 2006, "1st");

        mockMvc.perform(get("/api/books").param("title", "effective"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].author").value("Joshua Bloch"));

        mockMvc.perform(get("/api/books").param("author", "goetz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Java Concurrency in Practice"));

        mockMvc.perform(get("/api/books").param("id", "joshua_bloch_effective_java_2018_3rd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updateBook_changesFieldsButKeepsGeneratedId() throws Exception {
        String bookId = createBook("1984", "George Orwell", 1949, "1st");

        mockMvc.perform(put("/api/books/{bookId}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("Nineteen Eighty-Four", "George Orwell", 1949, "2nd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.title").value("Nineteen Eighty-Four"))
                .andExpect(jsonPath("$.edition").value("2nd"));
    }

    @Test
    void updateBook_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/books/{bookId}", "missing_book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("Title", "Author", 2020, "1st"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_removesItFromSubsequentListing() throws Exception {
        String bookId = createBook("Brave New World", "Aldous Huxley", 1932, "1st");

        mockMvc.perform(delete("/api/books/{bookId}", bookId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteBook_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/books/{bookId}", "missing_book"))
                .andExpect(status().isNotFound());
    }

    @Test
    void borrowBook_marksBookUnavailable() throws Exception {
        String bookId = createBook("Effective Java", "Joshua Bloch", 2018, "3rd");
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.borrowerId").value(borrowerId));

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].available").value(false));
    }

    @Test
    void borrowBook_whenAlreadyBorrowed_returnsConflict() throws Exception {
        String bookId = createBook("1984", "George Orwell", 1949, "1st");
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        BorrowRequest borrowRequest = new BorrowRequest(borrowerId);

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(borrowRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(borrowRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void borrowBook_whenBorrowerNotFound_returnsNotFound() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st");

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest("missing_borrower"))))
                .andExpect(status().isNotFound());
    }

    private String createBook(String title, String author, int year, String edition) throws Exception {
        String json = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest(title, author, year, edition))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private String createBorrower(String name, LocalDate dateOfBirth, String address) throws Exception {
        String json = mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowerRequest(name, dateOfBirth, address))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }
}
