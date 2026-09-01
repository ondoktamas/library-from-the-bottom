package library.dto;

public record BookResponse(
        String id,
        String title,
        String author,
        Integer yearOfPublication,
        String edition,
        Integer quantity
) {
}
