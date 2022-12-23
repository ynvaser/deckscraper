package systems.bdev.deckscraper.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import systems.bdev.deckscraper.model.AverageDeck;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.persistence.DeckRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EdhRecDeckScraperTest {
    @Autowired
    private EdhRecDeckScraper edhRecDeckScraper;
    @Autowired
    private DeckRepository deckRepository;

    @Test
    void shouldFindCommanders() {
        // When
        edhRecDeckScraper.persistCommandersAndDecks(Set.of(new Card("Chatterfang, Squirrel General")), 80);

        // Then
        assertThat(deckRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldFindAverageDecks() {
        // When
        Set<AverageDeck> averageDecks = edhRecDeckScraper.fetchAverageDecks(Set.of(new Card("Chatterfang, Squirrel General")));

        // Then
        assertThat(averageDecks.size()).isGreaterThan(0);
    }
}