package com.ondok.library.util;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Generates the lowercase, underscore-joined IDs used as primary keys for
 * books ({@code author_title_yearofpublication_edition}) and borrowers
 * ({@code name_dateofbirth}).
 */
public final class IdGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern LEADING_TRAILING_UNDERSCORE = Pattern.compile("^_+|_+$");

    private IdGenerator() {
    }

    public static String bookId(String author, String title, int yearOfPublication, String edition) {
        return String.join("_", slug(author), slug(title), String.valueOf(yearOfPublication), slug(edition));
    }

    public static String borrowerId(String name, LocalDate dateOfBirth) {
        return String.join("_", slug(name), dateOfBirth.format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private static String slug(String value) {
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("");
        String lowerCased = withoutDiacritics.toLowerCase(Locale.ROOT);
        String underscored = NON_ALPHANUMERIC.matcher(lowerCased).replaceAll("_");
        return LEADING_TRAILING_UNDERSCORE.matcher(underscored).replaceAll("");
    }
}
