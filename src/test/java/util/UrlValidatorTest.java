package util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UrlValidatorTest {

    @Test
    void validate_acceptsHttpAndHttps() {
        assertDoesNotThrow(() -> UrlValidator.validate("http://example.com"));
        assertDoesNotThrow(() -> UrlValidator.validate("https://example.com/path?q=1"));
    }

    @Test
    void validate_rejectsNonHttpScheme() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("ftp://example.com"));
        assertTrue(ex.getMessage().toLowerCase().contains("http"));
    }

    @Test
    void validate_rejectsUrlWithoutHost() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.validate("https:///path"));
        assertTrue(ex.getMessage().toLowerCase().contains("домен") || ex.getMessage().toLowerCase().contains("url"));
    }

    @Test
    void validate_rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> UrlValidator.validate("%%%"));
    }
}