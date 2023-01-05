package systems.bdev.deckscraper.model;

import lombok.Data;
import org.springframework.data.util.Pair;
import systems.bdev.deckscraper.util.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
public final class AverageDeck implements Cardholder {
    private final Card commander;
    private final String tribe;
    private Map<Card, Long> cardsAndCounts;
    private String cardHash;
    private Integer percentage;

    public AverageDeck(Card commander, String tribe, Map<Card, Long> cardsAndCounts) {
        this.commander = commander;
        this.tribe = tribe;
        this.cardsAndCounts = cardsAndCounts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AverageDeck deck = (AverageDeck) o;
        return commander.equals(deck.commander) && cardsAndCounts.equals(deck.cardsAndCounts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commander, cardsAndCounts);
    }

    @Override
    public Map<Card, Long> getCardsAndCounts() {
        return cardsAndCounts;
    }

    public List<Card> getCards() {
        List<Card> result = new ArrayList<>();
        cardsAndCounts.forEach((card, count) -> {
            for (long i = 0; i < count; i++) {
                result.add(card);
            }
        });
        return result;
    }

    public void setCards(Collection<Card> cards) {
        cardsAndCounts = cards
                .stream()
                .collect(Collectors.groupingBy(Card::name))
                .entrySet()
                .stream()
                .map(entry -> Pair.of(new Card(entry.getKey()), (long) entry.getValue().size()))
                .collect(Pair.toMap());
    }

    @Override
    public String toFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("https://edhrec.com/average-decks/");
        sb.append(Utils.cardNameToJsonFileName(commander.name()));
        if (tribe != null && !"default".equals(tribe)) {
            sb.append("/");
            sb.append(tribe);
        }
        sb.append("\n\n");
        if (commander.isCombined()) {
            sb
                    .append("1 ")
                    .append(commander.parts().getFirst().name())
                    .append("\n")
                    .append("1 ")
                    .append(commander.parts().getSecond().name())
                    .append("\n");
        } else {
            sb
                    .append("1 ")
                    .append(commander.name())
                    .append("\n");
        }
        cardsAndCounts.keySet().stream().sorted(Comparator.comparing(Card::name)).forEach(card -> sb
                .append(cardsAndCounts.get(card))
                .append(" ")
                .append(card.name())
                .append("\n"));
        return sb.toString();
    }

    @Override
    public String getIdentifier() {
        return Utils.AVERAGE_DECK;
    }
}
