package library.dto;

import jakarta.validation.constraints.NotBlank;

public record BorrowRequest(
        @NotBlank String borrowerId
) {
}
