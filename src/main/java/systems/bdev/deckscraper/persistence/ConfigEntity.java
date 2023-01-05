package systems.bdev.deckscraper.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ConfigEntity {
    @Id
    private Integer id;
    @Column
    private String content;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigEntity configEntity = (ConfigEntity) o;
        return Objects.equals(id, configEntity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
