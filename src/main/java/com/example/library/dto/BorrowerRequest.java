package com.example.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record BorrowerRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z ]+", message = "must contain only letters and spaces") String name,
        @NotNull @Past LocalDate dateOfBirth,
        @NotBlank String address
) {
}
