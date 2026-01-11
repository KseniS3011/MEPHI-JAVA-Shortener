package storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import model.Link;

public class FileLinkRepository implements LinkRepository {

  private final Path file;
  private final Map<String, Link> byCode = new ConcurrentHashMap<>();

  private final ObjectMapper mapper;

  private final Object lock = new Object();

  public FileLinkRepository(Path file) {
    this.file = file;

    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new JavaTimeModule());
    this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    loadFromFile();
  }

  @Override
  public void save(Link link) {
    synchronized (lock) {
      byCode.put(link.code(), link);
      persistToFile();
    }
  }

  @Override
  public Optional<Link> findByCode(String code) {
    return Optional.ofNullable(byCode.get(code));
  }

  @Override
  public List<Link> findByOwner(String ownerUuid) {
    List<Link> res = new ArrayList<>();
    for (Link l : byCode.values()) {
      if (l.ownerUuid().equals(ownerUuid)) {
        res.add(l);
      }
    }
    res.sort(Comparator.comparing(Link::createdAt).reversed());
    return res;
  }

  @Override
  public void deleteByCode(String code) {
    synchronized (lock) {
      byCode.remove(code);
      persistToFile();
    }
  }

  @Override
  public List<Link> findAll() {
    return new ArrayList<>(byCode.values());
  }

  private void loadFromFile() {
    synchronized (lock) {
      try {
        if (!Files.exists(file)) {
          if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
          }
          persistToFile();
          return;
        }

        String json = Files.readString(file).trim();
        if (json.isEmpty()) {
          return;
        }

        List<Link> links = mapper.readValue(json, new TypeReference<List<Link>>() {});
        for (Link l : links) {
          byCode.put(l.code(), l);
        }

      } catch (IOException e) {
        throw new IllegalStateException("Не удалось загрузить файл: " + file.toAbsolutePath(), e);
      }
    }
  }

  private void persistToFile() {
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      List<Link> links = new ArrayList<>(byCode.values());
      links.sort(Comparator.comparing(Link::createdAt).reversed());

      String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(links);
      Files.writeString(file, json);

    } catch (IOException e) {
      throw new IllegalStateException("Не удалось сохранить файл: " + file.toAbsolutePath(), e);
    }
  }
}
