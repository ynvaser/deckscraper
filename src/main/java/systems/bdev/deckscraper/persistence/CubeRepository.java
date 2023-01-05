package systems.bdev.deckscraper.persistence;


import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

public interface CubeRepository extends JpaRepository<CubeEntity, String> {
    Stream<CubeEntity> findAllByDateUpdatedAfter(ZonedDateTime referenceDateTime);
}
