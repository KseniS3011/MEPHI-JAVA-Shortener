package service;

import model.Link;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import storage.FileLinkRepository;
import storage.LinkRepository;
import util.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

public class ShortenerServiceTest {

    @TempDir
    Path tempDir;

    private Config config(long ttlSeconds, int defaultMaxClicks) throws Exception {
        Path cfg = tempDir.resolve("app.properties");

        String props = """
                ttlSeconds=%d
                defaultMaxClicks=%d
                baseUrl=http://localhost/
                storageFile=%s
                userUuidFile=%s
                cleanupIntervalSeconds=30
                """.formatted(
                ttlSeconds,
                defaultMaxClicks,
                tempDir.resolve("links.json").toString().replace("\\", "\\\\"),
                tempDir.resolve("user.uuid").toString().replace("\\", "\\\\")
        );

        Files.writeString(cfg, props);
        return Config.load(cfg);
    }

    private ShortenerService newService(LinkRepository repo, Config config, Instant now) {
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        return new ShortenerService(repo, config, clock);
    }

    @Test
    void create_generatesUuidOnFirstCreate_andSavesToFile() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));

        assertNull(service.getCurrentUserUuid());

        Link link = service.create("https://mail.ru", 5);

        assertNotNull(service.getCurrentUserUuid());
        assertEquals(service.getCurrentUserUuid(), link.ownerUuid());

        String saved = Files.readString(Path.of(cfg.userUuidFile())).trim();
        assertEquals(service.getCurrentUserUuid(), saved);
    }

    @Test
    void create_usesDefaultLimitWhenNotProvided() throws Exception {
        Config cfg = config(3600, 7);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));

        Link link = service.create("https://mail.ru", null);

        assertEquals(7, link.maxClicks());
        assertEquals(0, link.clicksDone());
    }

    @Test
    void open_incrementsClicks() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));
        Link link = service.create("https://mail.ru", 5);

        service.open(link.code());

        // Проверим из репозитория (можно даже создать новый repo и перечитать файл)
        FileLinkRepository repoReload = new FileLinkRepository(Path.of(cfg.storageFile()));
        Link updated = repoReload.findByCode(link.code()).orElseThrow();
        assertEquals(1, updated.clicksDone());
    }

    @Test
    void open_blocksWhenLimitExceeded() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));
        Link link = service.create("https://mail.ru", 1);

        service.open(link.code());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.open(link.code()));
        assertTrue(ex.getMessage().toLowerCase().contains("лимит"));
    }

    @Test
    void open_deletesLinkWhenExpired() throws Exception {
        Config cfg = config(10, 10); // TTL = 10 секунд
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        ShortenerService serviceAtT0 = newService(repo, cfg, t0);
        Link link = serviceAtT0.create("https://mail.ru", 5);

        // Создаём новый сервис с "поздним" временем (t0 + 11 сек) — ссылка должна считаться протухшей
        ShortenerService serviceLate = newService(repo, cfg, t0.plusSeconds(11));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> serviceLate.open(link.code()));
        assertTrue(ex.getMessage().toLowerCase().contains("ист"));

        FileLinkRepository repoReload = new FileLinkRepository(Path.of(cfg.storageFile()));
        assertTrue(repoReload.findByCode(link.code()).isEmpty(), "Протухшая ссылка должна быть удалена");
    }

    @Test
    void deleteMine_deniesAccessForAnotherUser() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));
        Link link = service.create("https://mail.ru", 5);

        service.newUser(); // переключились на другого пользователя

        SecurityException ex = assertThrows(SecurityException.class, () -> service.deleteMine(link.code()));
        assertTrue(ex.getMessage().toLowerCase().contains("доступ"));
    }

    @Test
    void updateLimitMine_changesLimitForOwner() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));
        Link link = service.create("https://mail.ru", 5);

        service.updateLimitMine(link.code(), 20);

        FileLinkRepository repoReload = new FileLinkRepository(Path.of(cfg.storageFile()));
        Link updated = repoReload.findByCode(link.code()).orElseThrow();
        assertEquals(20, updated.maxClicks());
    }

    @Test
    void listMine_returnsOnlyUsersLinks() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));

        Link a = service.create("https://mail.ru", 5);
        String firstUser = a.ownerUuid();

        service.newUser();
        service.create("https://ya.ru", 5);

        service.switchUser(firstUser);

        assertEquals(1, service.listMine().size());
        assertEquals("https://mail.ru", service.listMine().get(0).originalUrl());
    }

    @Test
    void sameUrl_twoUsers_getDifferentCodes() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));

        Link a = service.create("https://mail.ru", 5);

        service.newUser();
        Link b = service.create("https://mail.ru", 5);

        assertNotEquals(a.code(), b.code());
        assertNotEquals(a.ownerUuid(), b.ownerUuid());
    }

    @Test
    void switchUser_rejectsInvalidUuid() throws Exception {
        Config cfg = config(3600, 10);
        FileLinkRepository repo = new FileLinkRepository(Path.of(cfg.storageFile()));

        ShortenerService service = newService(repo, cfg, Instant.parse("2026-01-01T00:00:00Z"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.switchUser("not-a-uuid"));
        assertTrue(ex.getMessage().toLowerCase().contains("uuid") || ex.getMessage().toLowerCase().contains("формат"));
    }
}