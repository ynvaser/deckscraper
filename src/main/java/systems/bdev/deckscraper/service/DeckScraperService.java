package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.input.DeckBoxCsvParser;
import systems.bdev.deckscraper.input.EdhRecDeckScraper;
import systems.bdev.deckscraper.input.ScryfallCommanderSearcher;
import systems.bdev.deckscraper.model.AverageDeck;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.persistence.DeckRepository;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static systems.bdev.deckscraper.util.Utils.createFolderIfNeeded;

@Service
@Slf4j
public class DeckScraperService {
    @Autowired
    private DeckBoxCsvParser inventoryParser;
    @Autowired
    private ScryfallCommanderSearcher scryfallCommanderSearcher;
    @Autowired
    private EdhRecDeckScraper edhRecDeckScraper;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    private DeckSaverService deckSaverService;

    public void run(String[] args) {
        try {
            File jarLocation = getJarLocation(args[0]);
            Path inputFolderPath = createFolderIfNeeded(jarLocation, "\\input");
            Path outputFolderPath = createFolderIfNeeded(jarLocation, "\\output");
            File[] inputFilesArray = inputFolderPath.toFile().listFiles();
            if (inputFilesArray != null && inputFilesArray.length != 0) {
                log.info("Files present in \"{}\", processing...", inputFolderPath);
                Set<Card> collection = new HashSet<>();
                for (File file : inputFilesArray) {
                    collection.addAll(inventoryParser.processInventory(file));
                }
                Set<Card> commanders = ConcurrentHashMap.newKeySet();
                commanders.addAll(scryfallCommanderSearcher.fetchCommanders());
                commanders.removeIf(card -> !collection.contains(card));
                if (!"true".equalsIgnoreCase(args[3])) {
                    commanders.parallelStream().forEach(commander -> edhRecDeckScraper.persistCommandersAndDecks(Set.of(commander), Integer.parseInt(args[2])));
                    log.info("Done with deck scraping!");
                } else {
                    log.info("Skipping deck scraping due to setting...");
                }
                deckSaverService.saveDecksFromDb(commanders, outputFolderPath, collection, Integer.parseInt(args[1]), Integer.parseInt(args[4]));
                createAverageDecks(outputFolderPath, collection, commanders, Integer.parseInt(args[5]));
            } else {
                log.error("No files present in input directory: {}", inputFolderPath);
            }
        } catch (Exception e) {
            log.error("Something went wrong :(", e);
        }

    }

    private void createAverageDecks(Path outputFolderPath, Set<Card> collection, Set<Card> commanders, Integer averageDeckThreshold) {
        Set<AverageDeck> averageDecks = edhRecDeckScraper.fetchAverageDecks(commanders);
        deckSaverService.saveAverageDecks(averageDecks, Path.of(outputFolderPath.toString(), "_average"), collection, averageDeckThreshold);
    }

    private File getJarLocation(String arg) {
        return new File(arg);
    }
}
