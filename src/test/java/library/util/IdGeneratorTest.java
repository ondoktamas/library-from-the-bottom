package library.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void bookId_joinsSlugifiedFieldsWithUnderscores() {
        String id = IdGenerator.bookId("Robert C. Martin", "Clean Code", 2008, "1st");

        assertThat(id).isEqualTo("robert_c_martin_clean_code_2008_1st");
    }

    @Test
    void bookId_lowercasesAndCollapsesNonAlphanumerics() {
        String id = IdGenerator.bookId("J.R.R. Tolkien", "The Lord of the Rings!", 1954, "50th Anniversary");

        assertThat(id).isEqualTo("j_r_r_tolkien_the_lord_of_the_rings_1954_50th_anniversary");
    }

    @Test
    void bookId_stripsDiacritics() {
        String id = IdGenerator.bookId("Gabriel García Márquez", "Cien Años de Soledad", 1967, "1st");

        assertThat(id).isEqualTo("gabriel_garcia_marquez_cien_anos_de_soledad_1967_1st");
    }

    @Test
    void borrowerId_joinsSlugifiedNameWithCompactDateOfBirth() {
        String id = IdGenerator.borrowerId("Jane Doe", LocalDate.of(1990, 5, 12));

        assertThat(id).isEqualTo("jane_doe_19900512");
    }
}
