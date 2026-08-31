package com.ondok.library.dto;

import java.time.LocalDate;

public record BorrowerResponse(
        String id,
        String name,
        LocalDate dateOfBirth,
        String address
) {
}
