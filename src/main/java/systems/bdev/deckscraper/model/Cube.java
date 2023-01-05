package systems.bdev.deckscraper.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import org.springframework.data.util.Pair;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static systems.bdev.deckscraper.util.Utils.CUBE_COMMANDER;

@Data
public class Cube implements Cardholder {
    private String id;
    private Map<Card, Long> cardsAndCounts;
    @JsonAlias("date_updated")
    private ZonedDateTime dateUpdated;
    @JsonAlias("name")
    private String cubeName;
    @JsonAlias("users_following")
    private List<String> usersFollowing;

    private Integer percentage;


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
        setCardsAndCounts(cards
                .stream()
                .collect(Collectors.groupingBy(Card::name))
                .entrySet()
                .stream()
                .map(entry -> Pair.of(new Card(entry.getKey()), (long) entry.getValue().size()))
                .collect(Pair.toMap()));
    }

    @Override
    public void setPercentage(Integer points) {
        percentage = points;
    }

    @Override
    public Integer getPercentage() {
        return percentage;
    }

    @Override
    public String toFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("https://cubecobra.com/cube/overview/");
        sb.append(id);
        sb.append("\n\n");
        cardsAndCounts.keySet().stream().sorted(Comparator.comparing(Card::name)).forEach(card -> sb
                .append(cardsAndCounts.get(card))
                .append(" ")
                .append(card.name())
                .append("\n"));
        return sb.toString();
    }

    @Override
    public Card getCommander() {
        return CUBE_COMMANDER;
    }

    @Override
    public String getIdentifier() {
        return id;
    }
}
