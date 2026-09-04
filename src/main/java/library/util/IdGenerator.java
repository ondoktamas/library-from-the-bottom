package library.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates the lowercase, underscore-joined IDs used as primary keys for
 * books ({@code author_title_yearofpublication_edition}) and borrowers
 * ({@code name_dateofbirth}).
 *
 * <p><strong>A slug is a readable label, not an identity.</strong> It throws away
 * punctuation, diacritics and case, so genuinely different books can slug to the
 * same string ("Clean Code" and "Clean-Code" both become {@code clean_code}).
 * Whether two books are <em>the same book</em> is decided by
 * {@link #normalize(String)} instead, which keeps everything that distinguishes
 * them. Callers must not treat an equal slug as proof of equal identity.
 */
public final class IdGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_UNDERSCORE = Pattern.compile("^_+|_+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private IdGenerator() {
    }

    public static String bookId(String author, String title, int yearOfPublication, String edition) {
        return String.join("_", slug(author), slug(title), String.valueOf(yearOfPublication), slug(edition));
    }

    public static String borrowerId(String name, LocalDate dateOfBirth) {
        return String.join("_", slug(name), dateOfBirth.format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /**
     * Normalises a field for natural-key comparison. Only differences that no
     * librarian would call a different book are erased - surrounding and repeated
     * whitespace, and letter case. Punctuation and diacritics are deliberately
     * kept, so "Clean Code" and "Clean-Code", or "Garcia" and "García", remain
     * distinct books rather than being silently merged into one another.
     */
    public static String normalize(String value) {
        return WHITESPACE.matcher(value.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a value contributes anything to a generated ID. Values made up
     * entirely of characters the slug discards (punctuation only, or a script
     * the slug cannot transliterate) produce an empty segment, which would yield
     * unusable IDs such as {@code __2020_1st}.
     */
    public static boolean hasUsableSlug(String value) {
        return !slug(value).isEmpty();
    }

    private static String slug(String value) {
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("");
        String lowerCased = withoutDiacritics.toLowerCase(Locale.ROOT);
        String underscored = NON_ALPHANUMERIC.matcher(lowerCased).replaceAll("_");
        return LEADING_TRAILING_UNDERSCORE.matcher(underscored).replaceAll("");
    }
}
