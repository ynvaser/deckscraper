package systems.bdev.deckscraper.persistence;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.persistence.converter.DelimitedStringToSet;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class DeckEntity {
    @Id
    private String id;
    @Column
    private String hash;
    @Column
    private String commander;
    @Column(name = "save_date")
    private LocalDate saveDate;
    @Column
    @Lob
    @Convert(converter = DelimitedStringToSet.class)
    private Set<String> cards;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeckEntity that = (DeckEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static DeckEntity fromDeck(String id, Deck deck, String cardHash, LocalDate saveDate) {
        DeckEntity deckEntity = new DeckEntity();
        deckEntity.setId(id);
        deckEntity.setCommander(deck.getCommander().name());
        deckEntity.setCards(deck.getCards().stream().map(Card::name).map(String::toLowerCase).collect(Collectors.toSet()));
        deckEntity.setHash(cardHash);
        deckEntity.setSaveDate(saveDate);
        return deckEntity;
    }

    public Deck toDeck() {
        Deck deck = new Deck(new Card(commander), cards.stream().map(Card::new).collect(Collectors.toSet()));
        deck.setCardHash(hash);
        deck.setId(id);
        return deck;
    }
}
