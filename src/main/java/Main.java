import cli.ConsoleApp;
import service.CleanupService;
import service.ShortenerService;
import storage.FileLinkRepository;
import storage.LinkRepository;
import util.Config;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        Config config = Config.load(Path.of("config/app.properties"));

        LinkRepository repo = new FileLinkRepository(Path.of(config.storageFile()));
        ShortenerService shortenerService = new ShortenerService(repo, config);
        CleanupService cleanupService = new CleanupService(repo, config);

        cleanupService.start();

        ConsoleApp app = new ConsoleApp(shortenerService);
        app.run();

        cleanupService.stop();
    }
}
