package systems.bdev.deckscraper.input;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import systems.bdev.deckscraper.model.Card;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class DeckBoxCsvParser {
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT;

    public Set<Card> processInventory(final File file) {
        Map<Card, Integer> cardsAndCounts = new HashMap<>();
        try (InputStreamReader fileReader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8.newDecoder())) {
            CSVParser parsedCsv = CSV_FORMAT
                    .parse(fileReader);
            for (CSVRecord record : parsedCsv) {
                if (record.size() != 16) {
                    log.error("File {} line {} isn't of length 16! You need to import a Deckbox Inventory CSV here.", file.getName(), record.getRecordNumber());
                    throw new RuntimeException("File " + file.getName() + " line " + record.getRecordNumber() + " isn't of length 16! You need to import a Deckbox Inventory CSV here.");
                } else if ("Count".equalsIgnoreCase(record.get(0))) {
                    log.info("Skipping header.");
                } else {
                    Card card = new Card(record.get(2).replaceAll("\"", ""));
                    int numberOfCards = Integer.parseInt(record.get(0));
                    cardsAndCounts.merge(card, numberOfCards, Integer::sum);
                }
            }
        } catch (IOException e) {
            log.error("Couldn't open file: {}", file.getName(), e);
            throw new RuntimeException(e);
        }
        return cardsAndCounts.keySet();
    }
}
