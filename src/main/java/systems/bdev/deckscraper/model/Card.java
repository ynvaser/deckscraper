package systems.bdev.deckscraper.model;

import org.springframework.data.util.Pair;

import java.util.Objects;

public record Card(String name, Pair<Card, Card> parts, CardType cardType) {

    public Card(String name) {
        this(name, null, CardType.NORMAL);
    }

    public static Card combine(Card first, Card second) {
        String name;
        if (first.cardType == CardType.BACKGROUND) {
            name = second.name + " " + first.name;
        } else if (second.cardType == CardType.BACKGROUND) {
            name = first.name + " " + second.name;
        } else {
            name = first.name.compareTo(second.name) < 0 ? first.name + " " + second.name : second.name + " " + first.name;
        }
        return new Card(name, Pair.of(first, second), CardType.COMBINED);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        if (name != null) {
            return name.equalsIgnoreCase(card.name);
        } else {
            return card.name == null;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
