package storage;

import model.Link;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class FileLinkRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsFromFile() {
        Path file = tempDir.resolve("links.json");

        FileLinkRepository repo1 = new FileLinkRepository(file);

        Link link = new Link(
                "ABC123",
                "user-1",
                "https://mail.ru",
                "http://localhost/ABC123",
                5,
                0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );

        repo1.save(link);

        FileLinkRepository repo2 = new FileLinkRepository(file);

        Link loaded = repo2.findByCode("ABC123").orElseThrow();
        assertEquals("https://mail.ru", loaded.originalUrl());
        assertEquals("user-1", loaded.ownerUuid());
        assertEquals(5, loaded.maxClicks());
    }

    @Test
    void deleteRemovesFromFile() {
        Path file = tempDir.resolve("links.json");

        FileLinkRepository repo = new FileLinkRepository(file);

        Link link = new Link(
                "DEL111",
                "user-1",
                "https://mail.ru",
                "http://localhost/DEL111",
                5,
                0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );

        repo.save(link);
        repo.deleteByCode("DEL111");

        FileLinkRepository repoReload = new FileLinkRepository(file);
        assertTrue(repoReload.findByCode("DEL111").isEmpty());
    }
}