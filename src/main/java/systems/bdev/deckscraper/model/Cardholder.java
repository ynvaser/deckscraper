package systems.bdev.deckscraper.model;

import java.util.Collection;
import java.util.Map;

public interface Cardholder {
    Map<Card, Long> getCardsAndCounts();

    Collection<Card> getCards();

    void setPercentage(Integer points);

    Integer getPercentage();

    String toFile();

    Card getCommander();

    String getIdentifier();

    default String getTribe() {
        return "";
    }
}
