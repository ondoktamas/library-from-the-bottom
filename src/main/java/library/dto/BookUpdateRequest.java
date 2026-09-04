package library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body for {@code PUT /api/books/{bookId}}. Deliberately has no {@code quantity}:
 * that field tracks how many copies are currently free to borrow, and it is owned
 * by the add / borrow / return flows. Letting an edit overwrite it desynchronises
 * stock from the outstanding loans - editing a book with one copy on loan and
 * resending a quantity used to conjure copies out of nothing once that loan came
 * back.
 *
 * <p>Requests that still send {@code quantity} are accepted and the field is
 * ignored, so existing clients keep working.
 */
public record BookUpdateRequest(
        @NotBlank String title,
        @NotBlank String author,
        @NotNull @Positive Integer yearOfPublication,
        @NotBlank String edition
) {
}
