package com.ondok.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookRequest(
        @NotBlank String title,
        @NotBlank String author,
        @NotNull @Positive Integer yearOfPublication,
        @NotBlank String edition
) {
}
