package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public class Config {
    private final Duration ttl;
    private final int defaultMaxClicks;
    private final String baseUrl;
    private final String storageFile;
    private final String userUuidFile;
    private final Duration cleanupInterval;

    private Config(Duration ttl, int defaultMaxClicks, String baseUrl, String storageFile, String userUuidFile, Duration cleanupInterval) {
        this.ttl = ttl;
        this.defaultMaxClicks = defaultMaxClicks;
        this.baseUrl = baseUrl;
        this.storageFile = storageFile;
        this.userUuidFile = userUuidFile;
        this.cleanupInterval = cleanupInterval;
    }

    public static Config load(Path path) {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать config: " + path.toAbsolutePath(), e);
        }

        Duration ttl = Duration.ofSeconds(Long.parseLong(p.getProperty("ttlSeconds", "86400")));
        int defaultMaxClicks = Integer.parseInt(p.getProperty("defaultMaxClicks", "10"));
        String baseUrl = p.getProperty("baseUrl", "http://localhost/");
        String storageFile = p.getProperty("storageFile", "data/links.jsonl");
        String userUuidFile = p.getProperty("userUuidFile", "data/user.uuid");
        Duration cleanupInterval = Duration.ofSeconds(Long.parseLong(p.getProperty("cleanupIntervalSeconds", "30")));

        return new Config(ttl, defaultMaxClicks, baseUrl, storageFile, userUuidFile, cleanupInterval);
    }

    public Duration ttl() { return ttl; }
    public int defaultMaxClicks() { return defaultMaxClicks; }
    public String baseUrl() { return baseUrl; }
    public String storageFile() { return storageFile; }
    public String userUuidFile() { return userUuidFile; }
    public Duration cleanupInterval() { return cleanupInterval; }
}
