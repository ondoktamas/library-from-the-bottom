package library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import library.dto.BookRequest;
import library.dto.BorrowRequest;
import library.dto.BorrowerRequest;
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
        BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", 2008, "1st", 3);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("robert_c_martin_clean_code_2008_1st"))
                .andExpect(jsonPath("$.quantity").value(3));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='robert_c_martin_clean_code_2008_1st')]").exists());
    }

    @Test
    void addBook_withMissingFields_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"title":"","author":"","yearOfPublication":null,"edition":"","quantity":null}""";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withNegativeQuantity_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"title":"Dune","author":"Frank Herbert","yearOfPublication":1965,"edition":"1st","quantity":-1}""";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withNonNumericQuantity_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"title":"Dune","author":"Frank Herbert","yearOfPublication":1965,"edition":"1st","quantity":"three"}""";

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withMatchingGeneratedId_increasesQuantityInsteadOfDuplicating() throws Exception {
        BookRequest request = new BookRequest("Dune", "Frank Herbert", 1965, "1st", 2);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("frank_herbert_dune_1965_1st"))
                .andExpect(jsonPath("$.quantity").value(4));

        mockMvc.perform(get("/api/books").param("id", "frank_herbert_dune_1965_1st"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(4));
    }

    @Test
    void listBooks_filtersByTitleAuthorAndId() throws Exception {
        createBook("Effective Java", "Joshua Bloch", 2018, "3rd", 2);
        createBook("Java Concurrency in Practice", "Brian Goetz", 2006, "1st", 2);

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
        String bookId = createBook("1984", "George Orwell", 1949, "1st", 2);

        mockMvc.perform(put("/api/books/{bookId}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("Nineteen Eighty-Four", "George Orwell", 1949, "2nd", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.title").value("Nineteen Eighty-Four"))
                .andExpect(jsonPath("$.edition").value("2nd"))
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void updateBook_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/books/{bookId}", "missing_book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("Title", "Author", 2020, "1st", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_removesItFromSubsequentListing() throws Exception {
        String bookId = createBook("Brave New World", "Aldous Huxley", 1932, "1st", 2);

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
    void borrowBook_decrementsQuantity() throws Exception {
        String bookId = createBook("Effective Java", "Joshua Bloch", 2018, "3rd", 1);
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(bookId))
                .andExpect(jsonPath("$.borrowerId").value(borrowerId));

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].quantity").value(0));
    }

    @Test
    void borrowBook_allowsDifferentBorrowersUntilQuantityRunsOut() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st", 2);
        String borrowerA = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        String borrowerB = createBorrower("John Smith", LocalDate.of(1985, 1, 1), "1 Elm Street");

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerA))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerB))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].quantity").value(0));
    }

    @Test
    void borrowBook_whenQuantityDepleted_returnsConflict() throws Exception {
        String bookId = createBook("1984", "George Orwell", 1949, "1st", 1);
        String borrowerA = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        String borrowerB = createBorrower("John Smith", LocalDate.of(1985, 1, 1), "1 Elm Street");

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerA))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerB))))
                .andExpect(status().isConflict());
    }

    @Test
    void borrowBook_whenSameBorrowerAlreadyHasIt_returnsConflict() throws Exception {
        String bookId = createBook("Brave New World", "Aldous Huxley", 1932, "1st", 2);
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

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].quantity").value(1));
    }

    @Test
    void borrowBook_whenBorrowerNotFound_returnsNotFound() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st", 2);

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest("missing_borrower"))))
                .andExpect(status().isNotFound());
    }

    private String createBook(String title, String author, int year, String edition, int quantity) throws Exception {
        String json = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest(title, author, year, edition, quantity))))
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
