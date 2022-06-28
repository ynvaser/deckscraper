package systems.bdev.deckscraper.model;

import java.util.Set;

public interface Cardholder {
    Set<Card> getCardsAsSet();

    void setPercentage(Integer points);

    Integer getPercentage();

    String toFile();

    Card getCommander();
}
