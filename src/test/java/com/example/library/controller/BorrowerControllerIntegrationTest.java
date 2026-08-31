package com.example.library.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.library.dto.BookRequest;
import com.example.library.dto.BorrowRequest;
import com.example.library.dto.BorrowerRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "spring.sql.init.mode=never")
class BorrowerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createBorrower_generatesIdFromNameAndDateOfBirth() throws Exception {
        BorrowerRequest request = new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("jane_doe_19900512"))
                .andExpect(jsonPath("$.address").value("123 Main St"));
    }

    @Test
    void createBorrower_withInvalidName_returnsBadRequest() throws Exception {
        BorrowerRequest request = new BorrowerRequest("Jane123", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBorrower_withDuplicateGeneratedId_returnsConflict() throws Exception {
        BorrowerRequest request = new BorrowerRequest("John Smith", LocalDate.of(1985, 1, 1), "1 Elm Street");

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void listBorrowers_returnsAllRegisteredBorrowers() throws Exception {
        createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");
        createBorrower("John Smith", LocalDate.of(1985, 1, 1), "1 Elm Street");

        mockMvc.perform(get("/api/borrowers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='jane_doe_19900512')]").exists())
                .andExpect(jsonPath("$[?(@.id=='john_smith_19850101')]").exists());
    }

    @Test
    void createBorrower_thenGetBorrower_returnsSameBorrower() throws Exception {
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(get("/api/borrowers/{id}", borrowerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-12"));
    }

    @Test
    void getBorrower_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/borrowers/{id}", "missing_borrower"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBorrower_changesFieldsButKeepsGeneratedId() throws Exception {
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(put("/api/borrowers/{id}", borrowerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "456 Oak Ave"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(borrowerId))
                .andExpect(jsonPath("$.address").value("456 Oak Ave"));
    }

    @Test
    void updateBorrower_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/borrowers/{id}", "missing_borrower")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new BorrowerRequest("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBorrower_removesThemFromSubsequentListing() throws Exception {
        String borrowerId = createBorrower("Jane Doe", LocalDate.of(1990, 5, 12), "123 Main St");

        mockMvc.perform(delete("/api/borrowers/{id}", borrowerId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/borrowers/{id}", borrowerId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBorrower_whenNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/borrowers/{id}", "missing_borrower"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBorrowedBooks_returnsBooksBorrowedByBorrower() throws Exception {
        String borrowerId = createBorrower("John Smith", LocalDate.of(1985, 1, 1), "1 Elm Street");
        String bookId = createBook("The Pragmatic Programmer", "Andrew Hunt", 1999, "1st");

        mockMvc.perform(post("/api/books/{bookId}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowRequest(borrowerId))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/borrowers/{id}/books", borrowerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookId));
    }

    @Test
    void getBorrowedBooks_whenBorrowerNotFound_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/borrowers/{id}/books", "missing_borrower"))
                .andExpect(status().isNotFound());
    }

    private String createBorrower(String name, LocalDate dateOfBirth, String address) throws Exception {
        String json = mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BorrowerRequest(name, dateOfBirth, address))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private String createBook(String title, String author, int year, String edition) throws Exception {
        String json = mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookRequest(title, author, year, edition))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }
}
