package systems.bdev.deckscraper.persistence;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeckRepository extends JpaRepository<DeckEntity, String> {
    List<DeckEntity> findAllByCommander(String name);
}
