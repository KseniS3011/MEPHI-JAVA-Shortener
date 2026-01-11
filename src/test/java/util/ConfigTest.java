package util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ConfigTest {

  @TempDir Path tempDir;

  @Test
  void loadsProperties() throws Exception {
    Path cfg = tempDir.resolve("app.properties");

    Files.writeString(
        cfg,
        """
                ttlSeconds=120
                defaultMaxClicks=3
                baseUrl=http://localhost/
                storageFile=data/links.json
                userUuidFile=data/user.uuid
                cleanupIntervalSeconds=7
                """);

    Config c = Config.load(cfg);

    assertEquals(120, c.ttl().toSeconds());
    assertEquals(3, c.defaultMaxClicks());
    assertEquals("http://localhost/", c.baseUrl());
    assertEquals("data/links.json", c.storageFile());
    assertEquals("data/user.uuid", c.userUuidFile());
    assertEquals(7, c.cleanupInterval().toSeconds());
  }
}
