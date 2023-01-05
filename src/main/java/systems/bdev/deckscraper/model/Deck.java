package systems.bdev.deckscraper.model;

import lombok.Data;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
public final class Deck implements Cardholder {
    private final Card commander;
    private final Set<Card> cards;
    private String id;
    private String cardHash;
    private Integer percentage;

    public Deck(Card commander, Set<Card> cards) {
        this.commander = commander;
        this.cards = cards;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deck deck = (Deck) o;
        return commander.equals(deck.commander) && cards.equals(deck.cards);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commander, cards);
    }

    @Override
    public Map<Card, Long> getCardsAndCounts() {
        return cards.stream().collect(Collectors.toMap(Function.identity(), (card) -> 1L));
    }

    public String toFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("https://edhrec.com/deckpreview/");
        sb.append(id);
        sb.append("\n\n");
        String body = cards.stream()
                .map(Card::name)
                .sorted()
                .collect(
                        Collectors.joining(
                                "\n",
                                commander.isCombined() ? commander.parts().getFirst().name() + "\n" + commander.parts().getSecond().name() + "\n" : commander.name() + "\n",
                                ""));
        sb.append(body);
        return sb.toString();
    }

    @Override
    public String getIdentifier() {
        return id;
    }
}
