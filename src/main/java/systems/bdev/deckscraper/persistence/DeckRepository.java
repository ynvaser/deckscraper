package systems.bdev.deckscraper.persistence;


import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

public interface DeckRepository extends JpaRepository<DeckEntity, String> {
    List<DeckEntity> findAllByCommander(String name);

    Stream<DeckEntity> findAllBySaveDateAfter(LocalDate referenceDate);
}
