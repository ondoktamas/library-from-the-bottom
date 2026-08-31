package com.example.library.dto;

public record BookResponse(
        String id,
        String title,
        String author,
        Integer yearOfPublication,
        String edition,
        boolean available
) {
}
