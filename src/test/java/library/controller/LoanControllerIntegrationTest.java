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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.sql.init.mode=never")
class LoanControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listLoans_returnsActiveLoanWithBookAndBorrowerSummary() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st");
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        borrowBook(bookId, borrowerId);

        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bookId=='" + bookId + "')]").exists())
                .andExpect(jsonPath("$[0].bookTitle").value("Dune"))
                .andExpect(jsonPath("$[0].borrowerName").value("Jane Doe"));
    }

    @Test
    void returnBook_incrementsQuantityAndRemovesLoan() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st");
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        String loanJson = borrowBook(bookId, borrowerId);
        Long loanId = objectMapper.readTree(loanJson).get("loanId").asLong();

        mockMvc.perform(delete("/api/loans/{loanId}", loanId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].quantity").value(1));
        mockMvc.perform(get("/api/loans"))
                .andExpect(jsonPath("$[?(@.bookId=='" + bookId + "')]").doesNotExist());
    }

    @Test
    void returnBook_thenSameBorrowerCanBorrowSameBookAgain() throws Exception {
        String bookId = createBook("Dune", "Frank Herbert", 1965, "1st");
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        String loanJson = borrowBook(bookId, borrowerId);
        Long loanId = objectMapper.readTree(loanJson).get("loanId").asLong();

        mockMvc.perform(delete("/api/loans/{loanId}", loanId))
                .andExpect(status().isNoContent());

        borrowBook(bookId, borrowerId);

        mockMvc.perform(get("/api/books").param("id", bookId))
                .andExpect(jsonPath("$[0].quantity").value(0));
    }

    @Test
    void returnBook_whenLoanNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/loans/{loanId}", 999999))
                .andExpect(status().isNotFound());
    }

    private String createBook(String title, String author, int year, String edition) throws Exception {
        String json = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest(title, author, year, edition, 1))))
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

    private String borrowBook(String bookId, String borrowerId) throws Exception {
        return mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
