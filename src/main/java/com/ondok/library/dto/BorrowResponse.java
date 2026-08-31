package com.ondok.library.dto;

import java.time.Instant;

public record BorrowResponse(
        Long loanId,
        String bookId,
        String borrowerId,
        Instant borrowedAt
) {
}
