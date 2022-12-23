package systems.bdev.deckscraper.model;

import lombok.Data;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Data
public final class AverageDeck implements Cardholder {
    private final Card commander;
    private final String tribe;
    private final Map<Card, Long> cards;
    private String cardHash;
    private Integer percentage;

    public AverageDeck(Card commander, String tribe, Map<Card, Long> cards) {
        this.commander = commander;
        this.tribe = tribe;
        this.cards = cards;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AverageDeck deck = (AverageDeck) o;
        return commander.equals(deck.commander) && cards.equals(deck.cards);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commander, cards);
    }

    @Override
    public String toFile() {
        StringBuilder sb = new StringBuilder()
                .append("1 ")
                .append(commander.name())
                .append("\n");
        cards.forEach((card, amount) -> sb
                .append(amount)
                .append(" ")
                .append(card.name())
                .append("\n"));
        return sb.toString();
    }

    @Override
    public Set<Card> getCardsAsSet() {
        return cards.keySet();
    }
}
