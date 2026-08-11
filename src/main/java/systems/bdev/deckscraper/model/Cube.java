package systems.bdev.deckscraper.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.springframework.data.util.Pair;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static systems.bdev.deckscraper.util.Utils.CUBE_COMMANDER;

@Data
public class Cube implements Cardholder {
    @JsonProperty("id")
    private String id;

    private Map<Card, Long> cardsAndCounts;

    private ZonedDateTime dateUpdated;

    @JsonProperty("date_last_updated")
    public void setDateLastUpdated(JsonNode node) {
        if (node != null && node.isNumber()) {
            this.dateUpdated = ZonedDateTime.ofInstant(Instant.ofEpochMilli(node.asLong()), ZoneId.of("UTC"));
        }
    }

    @JsonProperty("name")
    private String cubeName;

    @JsonIgnore
    private int followerCount = 0;

    @JsonProperty("following")
    public void setFollowing(JsonNode node) {
        if (node != null && node.isArray()) {
            this.followerCount = node.size();
        }
    }

    public int getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(int followerCount) {
        this.followerCount = followerCount;
    }

    @JsonProperty("cards")
    private JsonNode rawCardsNode;

    private Integer percentage;


    public List<Card> getCards() {
        List<Card> result = new ArrayList<>();
        if (cardsAndCounts != null) {
            cardsAndCounts.forEach((card, count) -> {
                if (card != null && count != null) {
                    for (long i = 0; i < count; i++) {
                        result.add(card);
                    }
                }
            });
        }
        return result;
    }

    @JsonIgnore
    public void setCards(Collection<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            setCardsAndCounts(null);
            return;
        }
        setCardsAndCounts(cards
                .stream()
                .filter(card -> card != null && card.name() != null)
                .collect(Collectors.groupingBy(Card::name))
                .entrySet()
                .stream()
                .map(entry -> Pair.of(new Card(entry.getKey()), (long) entry.getValue().size()))
                .collect(Pair.toMap()));
    }

    public List<String> getResolvedCardNames(Map<Integer, String> indexToNameMap) {
        List<String> result = new ArrayList<>();
        if (rawCardsNode != null && rawCardsNode.isArray()) {
            for (JsonNode elem : rawCardsNode) {
                if (elem.isNumber()) {
                    int index = elem.asInt();
                    String name = indexToNameMap != null ? indexToNameMap.get(index) : null;
                    if (name != null && !name.isBlank()) {
                        Card card = new Card(name.trim());
                        if (card.name() != null && !card.name().isBlank()) {
                            result.add(card.name().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        } else if (cardsAndCounts != null) {
            cardsAndCounts.forEach((card, count) -> {
                if (card != null && card.name() != null) {
                    long num = count != null ? count : 1;
                    String normalizedName = new Card(card.name()).name().toLowerCase(java.util.Locale.ROOT);
                    for (long i = 0; i < num; i++) {
                        result.add(normalizedName);
                    }
                }
            });
        }
        return result;
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
        sb.append(id != null ? id : "");
        sb.append("\n\n");
        if (cardsAndCounts != null) {
            cardsAndCounts.keySet().stream()
                    .filter(card -> card != null && card.name() != null)
                    .sorted(Comparator.comparing(Card::name))
                    .forEach(card -> sb
                            .append(cardsAndCounts.get(card))
                            .append(" ")
                            .append(card.name())
                            .append("\n"));
        }
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
