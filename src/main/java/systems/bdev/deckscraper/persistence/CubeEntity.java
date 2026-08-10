package systems.bdev.deckscraper.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Cube;
import systems.bdev.deckscraper.persistence.converter.DelimitedStringToList;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class CubeEntity {
    @Id
    private String id;
    @Column
    @Convert(converter = DelimitedStringToList.class)
    @Lob
    private List<String> cards;
    @Column
    private ZonedDateTime dateUpdated;
    @Column
    private String cubeName;
    @Column
    @Convert(converter = DelimitedStringToList.class)
    @Lob
    private List<String> usersFollowing;

    public Cube toCube() {
        Cube cube = new Cube();
        cube.setId(id);
        cube.setCards(cards != null ? cards.stream().map(Card::new).collect(Collectors.toList()) : List.of());
        cube.setDateUpdated(dateUpdated);
        cube.setCubeName(cubeName);
        cube.setUsersFollowing(usersFollowing != null ? usersFollowing : List.of());
        return cube;
    }

    public static CubeEntity fromCube(Cube cube) {
        CubeEntity cubeEntity = new CubeEntity();
        cubeEntity.setId(cube.getId());
        List<Card> cubeCards = cube.getCards();
        cubeEntity.setCards(cubeCards != null ? cubeCards.stream().map(Card::name).collect(Collectors.toList()) : List.of());
        cubeEntity.setDateUpdated(cube.getDateUpdated());
        String name = cube.getCubeName();
        if (name != null && name.length() > 255) {
            name = name.substring(0, 255);
        }
        cubeEntity.setCubeName(name);
        cubeEntity.setUsersFollowing(cube.getUsersFollowing() != null ? cube.getUsersFollowing() : List.of());
        return cubeEntity;
    }
}
