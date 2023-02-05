package systems.bdev.deckscraper.input;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import systems.bdev.deckscraper.model.Card;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class CsvParserService {
    private static final String CARD_FACE_SEPARATOR = "//";
    private static final List<String> ALIAS_QUANTITY = List.of("count", "amount", "quantity", "qty");
    private static final List<String> ALIAS_NAME = List.of("name", "card_name", "card");

    public Map<Card, Integer> processInventory(final File file, Map<String, String> incompleteDoubleFacedCardNameMap) {
        Map<Card, Integer> cardsAndCounts = new HashMap<>();
        try (InputStreamReader fileReader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8.newDecoder())) {
            CSVParser parsedCsv = CSVFormat.DEFAULT.withFirstRecordAsHeader()
                    .parse(fileReader);
            Pair<String, String> quantityAndName = identifyQuantityAndName(parsedCsv.getHeaderNames());
            for (CSVRecord record : parsedCsv) {
                String cardName = record.get(quantityAndName.getSecond()).replaceAll("\"", "").trim();
                if (cardName.contains(CARD_FACE_SEPARATOR)) {
                    String[] split = cardName.split(CARD_FACE_SEPARATOR);
                    List<String> nameComponents = new ArrayList<>();
                    for (String s : split) {
                        if (!s.isBlank()) {
                            nameComponents.add(s.trim());
                        }
                    }
                    cardName = String.join(" // ", nameComponents);
                } else if (incompleteDoubleFacedCardNameMap.containsKey(cardName.toLowerCase(Locale.ROOT))) {
                    cardName = incompleteDoubleFacedCardNameMap.get(cardName.toLowerCase(Locale.ROOT));
                }
                Card card = new Card(cardName);
                int numberOfCards = Integer.parseInt(record.get(quantityAndName.getFirst()));
                cardsAndCounts.merge(card, numberOfCards, Integer::sum);
            }
        } catch (IOException e) {
            log.error("Couldn't open file: {}", file.getName(), e);
            throw new RuntimeException(e);
        }
        return cardsAndCounts;
    }

    private Pair<String, String> identifyQuantityAndName(List<String> headerValues) {
        try {
            String quantity = headerValues.stream().filter(header -> ALIAS_QUANTITY.contains(header.replaceAll("\"", "").toLowerCase(Locale.ROOT))).findFirst().get();
            String name = headerValues.stream().filter(header -> ALIAS_NAME.contains(header.replaceAll("\"", "").toLowerCase(Locale.ROOT))).findFirst().get();
            return Pair.of(quantity, name);
        } catch (Exception e) {
            log.error("Your inventory header values couldn't be parsed. Make sure that your inventory csv file has a header, and it has a quantity and card name column. Accepted quantity column names (case insensitive): {}, accepted card name column names (case insensitive): {}", ALIAS_QUANTITY, ALIAS_NAME);
            throw new RuntimeException(e);
        }
    }
}
