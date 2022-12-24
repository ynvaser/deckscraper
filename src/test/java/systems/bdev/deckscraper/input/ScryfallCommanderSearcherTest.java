package systems.bdev.deckscraper.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import systems.bdev.deckscraper.model.Card;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ScryfallCommanderSearcherTest {
    @Autowired
    private ScryfallCommanderSearcher scryfallCommanderSearcher;

    @Test
    void shouldFindCommanders() {
        // When
        Set<Card> commanders = scryfallCommanderSearcher.fetchCommandersAndBackgrounds();

        // Then
        assertThat(commanders).isNotEmpty();
    }
}