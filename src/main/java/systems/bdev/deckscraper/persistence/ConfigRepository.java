package systems.bdev.deckscraper.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigRepository extends JpaRepository<ConfigEntity, Integer> {
}
