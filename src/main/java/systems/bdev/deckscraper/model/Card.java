package systems.bdev.deckscraper.model;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.util.Pair;

import java.util.Locale;
import java.util.Objects;

public record Card(String name, Pair<Card, Card> parts, CardType cardType) {

    public Card(String name) {
        this(name, null, CardType.NORMAL);
    }

    public Card(String name, Pair<Card, Card> parts, CardType cardType) {
        this.name = StringUtils.stripAccents(name);
        this.parts = parts;
        this.cardType = cardType;
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

    public boolean isCombined() {
        return cardType == CardType.COMBINED;
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
        if (name != null) {
            return Objects.hash(name.toLowerCase(Locale.ROOT));
        } else {
            return 0;
        }
    }
}
