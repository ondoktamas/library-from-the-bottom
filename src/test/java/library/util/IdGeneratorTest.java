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

    @Test
    void bookId_isLossy_soDifferentBooksCanShareOne() {
        // Documents exactly why an id is not an identity: these are two different
        // books, and the id alone cannot tell them apart.
        assertThat(IdGenerator.bookId("Robert C Martin", "Clean Code", 2008, "1st"))
                .isEqualTo(IdGenerator.bookId("Robert C. Martin!", "Clean-Code", 2008, "1st"));
    }

    @Test
    void normalize_ignoresCaseAndSurroundingAndRepeatedWhitespace() {
        assertThat(IdGenerator.normalize("  Clean   Code ")).isEqualTo("clean code");
        assertThat(IdGenerator.normalize("CLEAN CODE")).isEqualTo("clean code");
    }

    @Test
    void normalize_keepsWhatActuallyDistinguishesTwoBooks() {
        assertThat(IdGenerator.normalize("Clean-Code")).isNotEqualTo(IdGenerator.normalize("Clean Code"));
        assertThat(IdGenerator.normalize("Robert C. Martin")).isNotEqualTo(IdGenerator.normalize("Robert C Martin"));
        assertThat(IdGenerator.normalize("García")).isNotEqualTo(IdGenerator.normalize("Garcia"));
    }

    @Test
    void hasUsableSlug_rejectsValuesThatWouldProduceAnEmptySegment() {
        assertThat(IdGenerator.hasUsableSlug("Clean Code")).isTrue();
        assertThat(IdGenerator.hasUsableSlug("1984")).isTrue();
        assertThat(IdGenerator.hasUsableSlug("Cien Años")).as("diacritics transliterate").isTrue();
        assertThat(IdGenerator.hasUsableSlug("!!!")).isFalse();
        assertThat(IdGenerator.hasUsableSlug("   ")).isFalse();
    }
}
