package service;

import model.Link;
import storage.LinkRepository;
import util.CodeGenerator;
import util.Config;
import util.UrlValidator;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.Clock;

public class ShortenerService {
    private final LinkRepository repo;
    private final Config config;
    private final Clock clock;


    private String currentUserUuid;

    public ShortenerService(LinkRepository repo, Config config) {
        this(repo, config, Clock.systemUTC());
    }

    public ShortenerService(LinkRepository repo, Config config, Clock clock) {
        this.repo = repo;
        this.config = config;
        this.clock = clock;
        this.currentUserUuid = loadUserUuidIfExists();
    }

    public String getCurrentUserUuid() {
        return currentUserUuid;
    }

    public Link create(String originalUrl, Integer maxClicks) {
        UrlValidator.validate(originalUrl);

        if (currentUserUuid == null) {
            currentUserUuid = UUID.randomUUID().toString();
            saveUserUuid(currentUserUuid);
        }

        int limit = (maxClicks == null) ? config.defaultMaxClicks() : maxClicks;
        if (limit <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть больше нуля");
        }

        String code = generateUniqueCode();
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(config.ttl());

        String shortUrl = config.baseUrl().endsWith("/")
                ? config.baseUrl() + code
                : config.baseUrl() + "/" + code;

        Link link = new Link(code, currentUserUuid, originalUrl, shortUrl, limit, 0, now, expiresAt);
        repo.save(link);
        return link;
    }

    public void open(String code) {
        Link link = repo.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Ссылка не найдена"));

        if (Instant.now(clock).isAfter(link.expiresAt())) {
            System.out.println("Уведомление: срок действия ссылки истёк, ссылка будет удалена. Код: " + code);
            repo.deleteByCode(code);
            throw new IllegalStateException("Срок действия ссылки истёк");
        }

        if (link.clicksDone() >= link.maxClicks()) {
            System.out.println("Уведомление: лимит переходов по ссылке исчерпан. Код: " + code);
            throw new IllegalStateException("Лимит переходов по ссылке исчерпан");
        }

        Link updated = new Link(
                link.code(), link.ownerUuid(), link.originalUrl(), link.shortUrl(),
                link.maxClicks(), link.clicksDone() + 1,
                link.createdAt(), link.expiresAt()
        );
        repo.save(updated);

        openInBrowserOrPrint(link.originalUrl());
    }

    public List<Link> listMine() {
        ensureUserExists();
        return repo.findByOwner(currentUserUuid);
    }

    public void deleteMine(String code) {
        ensureUserExists();

        Link link = repo.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Ссылка не найдена"));

        if (!link.ownerUuid().equals(currentUserUuid)) {
            throw new SecurityException("Доступ запрещён: вы не являетесь владельцем ссылки");
        }
        repo.deleteByCode(code);
    }

    public void updateLimitMine(String code, int newLimit) {
        ensureUserExists();

        if (newLimit <= 0) {
            throw new IllegalArgumentException("Лимит переходов должен быть больше нуля");
        }

        Link link = repo.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Ссылка не найдена"));

        if (!link.ownerUuid().equals(currentUserUuid)) {
            throw new SecurityException("Доступ запрещён: вы не являетесь владельцем ссылки");
        }

        Link updated = new Link(
                link.code(), link.ownerUuid(), link.originalUrl(), link.shortUrl(),
                newLimit, link.clicksDone(),
                link.createdAt(), link.expiresAt()
        );
        repo.save(updated);
    }

    private void ensureUserExists() {
        if (currentUserUuid == null) {
            throw new IllegalStateException("UUID ещё не создан. Сначала создайте ссылку: create <url>");
        }
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = CodeGenerator.randomCode(6);
            if (repo.findByCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный код (слишком много совпадений)");
    }

    private String loadUserUuidIfExists() {
        Path path = Path.of(config.userUuidFile());

        if (!Files.exists(path)) {
            return null;
        }

        try {
            String content = Files.readString(path).trim();

            if (content.isEmpty()) {
                System.out.println("Предупреждение: файл UUID пустой, будет создан новый UUID");
                return null;
            }

            UUID.fromString(content);
            return content;

        } catch (IOException e) {
            System.out.println("Предупреждение: не удалось прочитать файл UUID, будет создан новый UUID");
            System.out.println("Причина: " + e.getMessage());
            return null;

        } catch (IllegalArgumentException e) {
            System.out.println("Предупреждение: в файле UUID некорректный формат, будет создан новый UUID");
            return null;
        }
    }

    private void saveUserUuid(String uuid) {
        Path path = Path.of(config.userUuidFile());

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, uuid);

        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить UUID в файл: " + path.toAbsolutePath(), e);
        }
    }

    public void switchUser(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("UUID не указан");
        }

        try {
            UUID.fromString(uuid);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный формат UUID");
        }

        this.currentUserUuid = uuid;
        saveUserUuid(uuid);
    }

    public void newUser() {
        String uuid = UUID.randomUUID().toString();
        this.currentUserUuid = uuid;
        saveUserUuid(uuid);
    }

    private void openInBrowserOrPrint(String url) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Автоматическое открытие браузера недоступно (headless-режим).");
            System.out.println("Откройте ссылку вручную: " + url);
            return;
        }

        try {
            URI uri = new URI(url);

            if (!Desktop.isDesktopSupported()) {
                System.out.println("Автоматическое открытие браузера не поддерживается на этой системе.");
                System.out.println("Откройте ссылку вручную: " + url);
                return;
            }

            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                System.out.println("На этой системе невозможно автоматически открыть браузер.");
                System.out.println("Откройте ссылку вручную: " + url);
                return;
            }

            desktop.browse(uri);
            System.out.println("Ссылка открыта в браузере: " + url);

        } catch (Exception e) {
            System.out.println("Не удалось автоматически открыть браузер: " + e.getMessage());
            System.out.println("Откройте ссылку вручную: " + url);
        }
    }
}