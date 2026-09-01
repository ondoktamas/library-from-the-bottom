package library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record BookRequest(
        @NotBlank String title,
        @NotBlank String author,
        @NotNull @Positive Integer yearOfPublication,
        @NotBlank String edition,
        @NotNull @PositiveOrZero Integer quantity
) {
}
