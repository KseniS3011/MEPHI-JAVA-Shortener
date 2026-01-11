package service;

import model.Link;
import storage.LinkRepository;
import util.Config;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupService {
    private final LinkRepository repo;
    private final Config config;
    private ScheduledExecutorService scheduler;

    public CleanupService(LinkRepository repo, Config config) {
        this.repo = repo;
        this.config = config;
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        long periodSec = Math.max(1, config.cleanupInterval().toSeconds());
        scheduler.scheduleAtFixedRate(this::cleanupExpired, periodSec, periodSec, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        for (Link l : repo.findAll()) {
            if (now.isAfter(l.expiresAt())) {
                repo.deleteByCode(l.code());
                System.out.println("Уведомление: срок действия ссылки (" + l.code() + ") истёк, ссылка удалена.");
            }
        }
    }
}