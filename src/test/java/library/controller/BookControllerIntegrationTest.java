package library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import library.dto.BookRequest;
import library.dto.BookUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    /**
     * Regression test for #3: the "adding a matching book merges instead of
     * duplicating" rule used to be checked against the slug id, which is frozen
     * at creation. After a rename the id no longer matched what the fields would
     * generate, so re-adding the very same book produced a second row.
     */
    @Test
    void addBook_afterARename_stillMergesIntoTheSameBook() throws Exception {
        String bookId = createBook("1984", "George Orwell", 1949, "1st", 2);

        mockMvc.perform(put("/api/books/{bookId}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookUpdateRequest("Nineteen Eighty-Four", "George Orwell", 1949, "1st"))))
                .andExpect(status().isOk());

        // Re-add under the NEW title: this is the same book, so it must merge.
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("Nineteen Eighty-Four", "George Orwell", 1949, "1st", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.quantity").value(3));

        mockMvc.perform(get("/api/books").param("author", "orwell"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /**
     * Regression test for #4: slugs are lossy, so two genuinely different books
     * could generate the same id. The second one used to be merged into the
     * first, silently discarding its own title and author.
     */
    @Test
    void addBook_whenADifferentBookSlugsTheSame_keepsBothWithItsOwnDetails() throws Exception {
        String first = createBook("Clean Code", "Robert C Martin", 2008, "1st", 1);

        String second = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("Clean-Code", "Robert C. Martin!", 2008, "1st", 7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Clean-Code"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin!"))
                .andExpect(jsonPath("$.quantity").value(7))
                .andReturn().getResponse().getContentAsString();

        String secondId = objectMapper.readTree(second).get("id").asText();
        assertThat(secondId).isNotEqualTo(first);
        assertThat(secondId).startsWith(first);

        mockMvc.perform(get("/api/books").param("author", "martin"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/books").param("id", first))
                .andExpect(jsonPath("$[0].title").value("Clean Code"))
                .andExpect(jsonPath("$[0].quantity").value(1));
    }

    @Test
    void addBook_ignoringCaseAndSpacing_stillCountsAsTheSameBook() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st", 1);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("  dune ", "frank  herbert", 1965, "1ST", 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    void addBook_withNoUsableSlug_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest("!!!", "???", 2020, "1st", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void updateBook_intoAnExistingBooksIdentity_returnsConflict() throws Exception {
        String orwell = createBook("1984", "George Orwell", 1949, "1st", 2);
        createBook("Animal Farm", "George Orwell", 1945, "1st", 1);

        mockMvc.perform(put("/api/books/{bookId}", orwell)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookUpdateRequest("Animal Farm", "George Orwell", 1945, "1st"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/books").param("id", orwell))
                .andExpect(jsonPath("$[0].title").value("1984"));
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
    void updateBook_changesFieldsButKeepsGeneratedIdAndAvailability() throws Exception {
        String bookId = createBook("1984", "George Orwell", 1949, "1st", 2);

        mockMvc.perform(put("/api/books/{bookId}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookUpdateRequest("Nineteen Eighty-Four", "George Orwell", 1949, "2nd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.title").value("Nineteen Eighty-Four"))
                .andExpect(jsonPath("$.edition").value("2nd"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void updateBook_ignoresAQuantityFieldSentByOlderClients() throws Exception {
        String bookId = createBook("1984", "George Orwell", 1949, "1st", 2);

        // The pre-change payload shape, quantity included. It must still be
        // accepted - and the quantity must still be ignored.
        String legacyJson = """
                {"title":"Nineteen Eighty-Four","author":"George Orwell","yearOfPublication":1949,"edition":"2nd","quantity":99}""";

        mockMvc.perform(put("/api/books/{bookId}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(legacyJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Nineteen Eighty-Four"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    /**
     * Regression test for the accounting bug: editing a book used to overwrite
     * availability with no regard for copies already out on loan, so returning
     * the loan afterwards credited a copy the library never owned.
     */
    @Test
    void updateBook_whileACopyIsOnLoan_doesNotInventCopiesWhenItComesBack() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st", 1);
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        String loanJson = mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long loanId = objectMapper.readTree(loanJson).get("loanId").asLong();

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].quantity").value(0));

        // Librarian corrects the edition while the only copy is out.
        mockMvc.perform(put("/api/books/{bookId}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookUpdateRequest("Dune", "Frank Herbert", 1965, "2nd"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0));

        mockMvc.perform(delete("/api/loans/{loanId}", loanId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].edition").value("2nd"))
                .andExpect(jsonPath("$[0].quantity").value(1));
    }

    @Test
    void updateBook_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/books/{bookId}", "missing_book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookUpdateRequest("Title", "Author", 2020, "1st"))))
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
