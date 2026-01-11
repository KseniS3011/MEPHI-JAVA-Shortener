package storage;

import model.Link;

import java.util.List;
import java.util.Optional;

public interface LinkRepository {
    void save(Link link);

    Optional<Link> findByCode(String code);

    List<Link> findByOwner(String ownerUuid);

    void deleteByCode(String code);

    List<Link> findAll();
}

