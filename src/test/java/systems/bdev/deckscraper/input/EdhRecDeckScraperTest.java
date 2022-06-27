package systems.bdev.deckscraper.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EdhRecDeckScraperTest {
    @Autowired
    private EdhRecDeckScraper edhRecDeckScraper;

    @Test
    void shouldFindCommanders() {
        // When
        Map<Card, Set<Deck>> decks = edhRecDeckScraper.getCommandersToDecks(Set.of(new Card("Chatterfang, Squirrel General")), Integer.parseInt(args[2]));

        // Then
        assertThat(decks).isNotEmpty();
    }
}