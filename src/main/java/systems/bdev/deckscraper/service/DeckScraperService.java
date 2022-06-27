package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.input.DeckBoxCsvParser;
import systems.bdev.deckscraper.input.EdhRecDeckScraper;
import systems.bdev.deckscraper.input.ScryfallCommanderSearcher;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.util.Utils;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DeckScraperService {
    @Autowired
    private DeckBoxCsvParser inventoryParser;
    @Autowired
    private ScryfallCommanderSearcher scryfallCommanderSearcher;
    @Autowired
    private EdhRecDeckScraper edhRecDeckScraper;

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
                Set<Card> commanders = scryfallCommanderSearcher.fetchCommanders();
                commanders.removeIf(card -> !collection.contains(card));
                Map<Card, Set<Deck>> allDecksForOwnedCommanders = new ConcurrentHashMap<>();
                commanders.parallelStream().forEach(commander-> allDecksForOwnedCommanders.putAll(edhRecDeckScraper.getCommandersToDecks(Set.of(commander), Integer.parseInt(args[2]))));
                Map<Card, Set<Deck>> decksAboveThreshold = allDecksForOwnedCommanders
                        .entrySet()
                        .stream()
                        .map(entry ->
                                Map.entry(
                                        entry.getKey(),
                                        entry.getValue()
                                                .stream()
                                                .filter(deck -> isAboveThreshold(deck, collection, Integer.parseInt(args[1])))
                                                .collect(Collectors.toSet())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                for (Map.Entry<Card, Set<Deck>> entry : decksAboveThreshold.entrySet()) {
                    Card commander = entry.getKey();
                    Set<Deck> decks = entry.getValue();
                    for (Deck deck : decks) {
                        String outputFileName = outputFolderPath + "\\" + Utils.cardNameToJsonFileName(commander.name()) + "\\" + deck.getPercentage() + "_" + deck.hashCode() + ".txt";
                        try (OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(outputFileName), StandardCharsets.UTF_8.newEncoder())) {
                            fileWriter.write(deck.toFile());
                        }
                    }
                }
            } else {
                log.error("No files present in input directory: {}", inputFolderPath);
            }
            log.info("Application finished, please check the output folder for results.");
        } catch (
                Exception e) {
            log.error("Something went wrong :(", e);
        } finally {
            pressButtonForExit();
        }

    }

    private boolean isAboveThreshold(Deck deck, Set<Card> collection, Integer percentage) {
        Set<Card> cards = deck.getCards();
        int points = 100 - cards.size(); //Max is 99, you already have the commander.
        for (Card card : cards) {
            if (collection.contains(card)) {
                points++;
            }
        }
        deck.setPercentage(points); // Dirty (no command/query separation), but simple
        return points >= percentage;
    }

    private void pressButtonForExit() {
        try {
            Console console = System.console();
            console.writer().println("Press ENTER to exit");
            console.readLine();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File getJarLocation(String arg) {
        return new File(arg);
    }

    private Path createFolderIfNeeded(File jarLocation, String folderName) throws IOException {
        File inputFolder = new File(jarLocation.getPath() + folderName);
        Path path = inputFolder.toPath();
        if (!inputFolder.exists()) {
            log.info("Folder \"{}\" doesn't exist, creating...", path);
            Files.createDirectories(path);
        } else {
            log.info("Folder \"{}\" exists, skipping creation...", path);
        }
        return path;
    }
}
