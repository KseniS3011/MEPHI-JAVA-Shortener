package util;

import java.net.URI;

public class UrlValidator {
    public static void validate(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();

            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("URL должен начинаться с http:// или https://");
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("В URL отсутствует домен (host)");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный URL: " + url);
        }
    }
}
