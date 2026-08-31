package com.ondok.library.dto;

import java.time.Instant;

public record LoanResponse(
        Long id,
        String bookId,
        String bookTitle,
        String borrowerId,
        String borrowerName,
        Instant borrowedAt
) {
}
