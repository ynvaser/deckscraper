package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.input.DeckBoxCsvParser;
import systems.bdev.deckscraper.input.EdhRecDeckScraper;
import systems.bdev.deckscraper.input.ScryfallCommanderSearcher;
import systems.bdev.deckscraper.model.AverageDeck;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.CardType;
import systems.bdev.deckscraper.persistence.DeckRepository;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
            int normalDeckThreshold = Integer.parseInt(args[1]);
            final int monthsToLookBack = Integer.parseInt(args[2]);
            boolean skipLookup = "true".equalsIgnoreCase(args[3]);
            int maxLands = Integer.parseInt(args[4]);
            int averageDeckThreshold = Integer.parseInt(args[5]);
            boolean searchUnownedCommanders = "true".equalsIgnoreCase(args[6]);

            Path inputFolderPath = createFolderIfNeeded(jarLocation, "\\input");
            Path outputFolderPath = createFolderIfNeeded(jarLocation, "\\output");
            File[] inputFilesArray = inputFolderPath.toFile().listFiles();
            if (inputFilesArray != null && inputFilesArray.length != 0) {
                log.info("Files present in \"{}\", processing...", inputFolderPath);
                Set<Card> collection = new HashSet<>();
                for (File file : inputFilesArray) {
                    collection.addAll(inventoryParser.processInventory(file));
                }
                Set<Card> commanders = parseCommanders(collection, searchUnownedCommanders);
                if (!skipLookup) {
                    commanders.parallelStream().forEach(commander -> edhRecDeckScraper.persistCommandersAndDecks(Set.of(commander), monthsToLookBack));
                    log.info("Done with deck scraping!");
                } else {
                    log.info("Skipping deck scraping due to setting...");
                }
                deckSaverService.saveDecksFromDb(commanders, outputFolderPath, collection, normalDeckThreshold, maxLands, monthsToLookBack);
                createAverageDecks(outputFolderPath, collection, commanders, averageDeckThreshold, maxLands);
            } else {
                log.error("No files present in input directory: {}", inputFolderPath);
            }
        } catch (Exception e) {
            log.error("Something went wrong :(", e);
        }

    }

    private Set<Card> parseCommanders(Set<Card> collection, boolean searchUnownedCommanders) {
        Set<Card> commanders = ConcurrentHashMap.newKeySet();
        Set<Card> commandersAndBackgrounds = scryfallCommanderSearcher.fetchCommandersAndBackgrounds();
        if (!searchUnownedCommanders) {
            commandersAndBackgrounds.removeIf(card -> !collection.contains(card));
        }
        Map<CardType, List<Card>> commandersAndBackgroundsByCardType = commandersAndBackgrounds.stream().collect(Collectors.groupingBy(Card::cardType));
        commandersAndBackgroundsByCardType.forEach((type, cards) -> {
            switch (type) {
                case NORMAL: {
                    commanders.addAll(cards);
                    break;
                }
                case PARTNER: {
                    commanders.addAll(cards);
                    parseCombinedCards(commanders, commandersAndBackgroundsByCardType, CardType.PARTNER, CardType.PARTNER);
                    break;
                }
                case FRIENDS_FOREVER: {
                    commanders.addAll(cards);
                    parseCombinedCards(commanders, commandersAndBackgroundsByCardType, CardType.FRIENDS_FOREVER, CardType.FRIENDS_FOREVER);
                    break;
                }
                case CHOOSE_A_BACKGROUND: {
                    commanders.addAll(cards);
                    parseCombinedCards(commanders, commandersAndBackgroundsByCardType, CardType.CHOOSE_A_BACKGROUND, CardType.BACKGROUND);
                    break;
                }
                case BACKGROUND: {
                    break;
                }
                default: {
                    throw new RuntimeException("Something went wrong :(");
                }
            }
        });
        return commanders;
    }

    private void parseCombinedCards(Set<Card> commanders, Map<CardType, List<Card>> commandersAndBackgroundsByCardType, CardType firstType, CardType secondType) {
        List<Card> firstSet = commandersAndBackgroundsByCardType.get(firstType);
        if (firstType.equals(secondType)) {
            for (int i = 0; i < firstSet.size(); i++) {
                for (int j = i + 1; j < firstSet.size(); j++) {
                    Card partner = firstSet.get(i);
                    Card otherPartner = firstSet.get(j);
                    if (!partner.equals(otherPartner)) {
                        commanders.add(Card.combine(partner, otherPartner));
                    }
                }
            }
        } else {
            List<Card> secondSet = commandersAndBackgroundsByCardType.get(secondType);
            firstSet.forEach(partner -> {
                secondSet.forEach(otherPartner -> {
                    if (!partner.equals(otherPartner)) {
                        commanders.add(Card.combine(partner, otherPartner));
                    }
                });
            });
        }
    }

    private void createAverageDecks(Path outputFolderPath, Set<Card> collection, Set<Card> commanders, Integer averageDeckThreshold, int maxLands) {
        Set<AverageDeck> averageDecks = edhRecDeckScraper.fetchAverageDecks(commanders);
        deckSaverService.saveAverageDecks(averageDecks, Path.of(outputFolderPath.toString(), "_average"), collection, averageDeckThreshold, maxLands);
    }

    private File getJarLocation(String arg) {
        return new File(arg);
    }
}
