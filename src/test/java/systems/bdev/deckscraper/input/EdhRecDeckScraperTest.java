package systems.bdev.deckscraper.input;

import org.junit.jupiter.api.Disabled;
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
    @Disabled //takes forever
    void shouldFindCommanders() {
        // When
        edhRecDeckScraper.persistCommandersAndDecks(Set.of(new Card("Chatterfang, Squirrel General")), 1);

        // Then
        assertThat(deckRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldFindAverageDecks() {
        // When
        Set<AverageDeck> averageDecks = edhRecDeckScraper.fetchAverageDecks(Set.of(new Card("Minsc & Boo, Timeless Heroes")));

        // Then
        assertThat(averageDecks.size()).isGreaterThan(0);
    }

    @Test
    void shouldExtractCardsFromNextDataHtml() {
        // Given
        String html = "<html><head><script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + "{\"props\":{\"pageProps\":{\"data\":{\"container\":{\"json_dict\":{\"cardlist\":["
                + "{\"header\":\"Creature\",\"cardviews\":[{\"name\":\"Sol Ring\"},{\"name\":\"Command Tower\"}]}"
                + "]}}}}}}"
                + "</script></head></html>";

        // When
        Set<String> cards = edhRecDeckScraper.extractCardsFromNextDataHtml(html);

        // Then
        assertThat(cards).containsExactlyInAnyOrder("Sol Ring", "Command Tower");
    }
}