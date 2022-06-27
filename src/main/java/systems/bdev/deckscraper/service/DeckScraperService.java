package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.input.DeckBoxCsvParser;
import systems.bdev.deckscraper.input.EdhRecDeckScraper;
import systems.bdev.deckscraper.input.ScryfallCommanderSearcher;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
                deckSaverService.saveDecks(jarLocation, outputFolderPath, collection, args);
            } else {
                log.error("No files present in input directory: {}", inputFolderPath);
            }
        } catch (Exception e) {
            log.error("Something went wrong :(", e);
        }

    }
    private File getJarLocation(String arg) {
        return new File(arg);
    }
}
