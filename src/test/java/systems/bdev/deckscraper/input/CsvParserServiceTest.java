package systems.bdev.deckscraper.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import systems.bdev.deckscraper.model.Card;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserServiceTest {
    CsvParserService underTest;

    @BeforeEach
    void setup() {
        underTest = new CsvParserService();
    }

    @ParameterizedTest
    @MethodSource("fileSource")
    void testFormat(final File file) {
        // Given
        Map<Card, Integer> expected = Map.of(
                new Card("A Little Chat"), 5,
                new Card("Abandon the Post"), 1,
                new Card("Abdel Adrian, Gorion's Ward"), 11,
                new Card("Akoum Warrior // Akoum Teeth"), 1,
                new Card("Agadeem's Awakening // Agadeem, the Undercrypt"), 2
        );
        Map<String, String> incompleteDoubleFacedCardNameMap = Map.of(
                "akoum warrior","Akoum Warrior // Akoum Teeth",
                "agadeem's awakening","Agadeem's Awakening // Agadeem, the Undercrypt"
        );
        // When
        Map<Card, Integer> result = underTest.processInventory(file, incompleteDoubleFacedCardNameMap);
        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private static Stream<File> fileSource() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = loader.getResource("csv");
        String path = url.getPath();
        return Arrays.stream(new File(path).listFiles());
    }
}