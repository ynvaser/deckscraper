package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.DeckScraperApplication;
import systems.bdev.deckscraper.input.CubeCobraService;
import systems.bdev.deckscraper.input.DeckBoxCsvParser;
import systems.bdev.deckscraper.input.EdhRecDeckScraper;
import systems.bdev.deckscraper.input.ScryfallCommanderSearcher;
import systems.bdev.deckscraper.model.AverageDeck;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.CardType;
import systems.bdev.deckscraper.persistence.DeckRepository;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static systems.bdev.deckscraper.util.Utils.SEPARATOR;
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
    @Autowired
    private CubeCobraService cubeCobraService;

    @Value("${config.normalDeckThreshold}")
    private int normalDeckThreshold;
    @Value("${config.monthsToLookBack}")
    private int monthsToLookBack;
    @Value("${config.skipNormalDeckLookup}")
    private boolean skipNormalDeckLookup;
    @Value("${config.maxLands}")
    private int maxLands;
    @Value("${config.averageDeckThreshold}")
    private int averageDeckThreshold;
    @Value("${config.searchUnownedCommanders}")
    private boolean searchUnownedCommanders;
    @Value("${config.skipCubeLookup}")
    private boolean skipCubeLookup;
    @Value("${config.cubeThreshold}")
    private int cubeThreshold;
    @Value("${config.popularCubeFollowerCount}")
    private int popularCubeFollowerCount;
    @Value("${config.disableNormalDecks}")
    private boolean disableNormalDecks;
    @Value("${config.disableAverageDecks}")
    private boolean disableAverageDecks;
    @Value("${config.disableCubes}")
    private boolean disableCubes;

    public void run(String[] args) {
        try {
            ApplicationHome applicationHome = new ApplicationHome(DeckScraperApplication.class);
            File jarLocation = applicationHome.getSource().getParentFile();
            printVariables(jarLocation);

            Path inputFolderPath = createFolderIfNeeded(jarLocation, SEPARATOR + "input");
            Path outputFolderPath = createFolderIfNeeded(jarLocation, SEPARATOR + "output");
            File[] inputFilesArray = inputFolderPath.toFile().listFiles();
            if (inputFilesArray != null && inputFilesArray.length != 0) {
                log.info("Files present in \"{}\", processing...", inputFolderPath);
                Map<Card, Integer> collection = new HashMap<>();
                for (File file : inputFilesArray) {
                    Map<Card, Integer> fileContents = inventoryParser.processInventory(file);
                    fileContents.forEach((key, value) -> collection.merge(key, value, Integer::sum));
                }
                Set<Card> commanders = parseCommanders(collection.keySet(), searchUnownedCommanders);
                if (!skipNormalDeckLookup && !disableNormalDecks) {
                    commanders.parallelStream().forEach(commander -> edhRecDeckScraper.persistCommandersAndDecks(Set.of(commander), monthsToLookBack));
                    log.info("Done with deck scraping!");
                } else {
                    log.info("Skipping deck scraping due to setting...");
                }
                if (!skipCubeLookup && !disableCubes) {
                    cubeCobraService.refreshCubeDatabase();
                }
                createNormalDecks(outputFolderPath, collection, commanders);
                createAverageDecks(outputFolderPath, collection.keySet(), commanders, averageDeckThreshold, maxLands);
                createCubes(outputFolderPath, collection, cubeThreshold, monthsToLookBack);
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
        if (firstSet != null && firstType.equals(secondType)) {
            for (int i = 0; i < firstSet.size(); i++) {
                for (int j = i + 1; j < firstSet.size(); j++) {
                    Card partner = firstSet.get(i);
                    Card otherPartner = firstSet.get(j);
                    if (!partner.equals(otherPartner)) {
                        commanders.add(Card.combine(partner, otherPartner));
                    }
                }
            }
        } else if (firstSet != null && commandersAndBackgroundsByCardType.containsKey(secondType)) {
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

    private void createNormalDecks(Path outputFolderPath, Map<Card, Integer> collection, Set<Card> commanders) {
        if (!disableNormalDecks) {
            deckSaverService.saveDecksFromDb(commanders, outputFolderPath, collection.keySet(), normalDeckThreshold, maxLands, monthsToLookBack);
        }
    }

    private void createAverageDecks(Path outputFolderPath, Set<Card> collection, Set<Card> commanders, Integer averageDeckThreshold, int maxLands) {
        if (!disableAverageDecks) {
            commanders.parallelStream().forEach(commander->{
                Set<AverageDeck> averageDecks = edhRecDeckScraper.fetchAverageDecks(Set.of(commander));
                deckSaverService.saveAverageDecks(averageDecks, Path.of(outputFolderPath.toString(), "_average"), collection, averageDeckThreshold, maxLands);
            });
        }
    }

    private void createCubes(Path outputFolderPath, Map<Card, Integer> collection, int cubeThreshold, int monthsToLookBack) {
        if (!disableCubes) {
            deckSaverService.saveCubes(outputFolderPath, collection, cubeThreshold, monthsToLookBack, popularCubeFollowerCount);
        }
    }

    private void printVariables(File jarLocation) {
        log.info("Application launched from: {}", jarLocation.getPath());
        log.info("Config: normalDeckThreshold: {}, monthsToLookBack: {}, skipNormalDeckLookup: {}, maxLands: {}, averageDeckThreshold: {}, searchUnownedCommanders: {}, skipCubeLookup: {}, cubeThreshold: {}, popularCubeFollowerCount: {}, ",
                normalDeckThreshold, monthsToLookBack, skipNormalDeckLookup, maxLands, averageDeckThreshold, searchUnownedCommanders, skipCubeLookup, cubeThreshold);
    }
}
