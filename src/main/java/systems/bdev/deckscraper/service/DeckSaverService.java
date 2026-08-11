package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Cardholder;
import systems.bdev.deckscraper.model.Cube;
import systems.bdev.deckscraper.persistence.CubeEntity;
import systems.bdev.deckscraper.persistence.CubeRepository;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static systems.bdev.deckscraper.util.Utils.SEPARATOR;

@Service
@Slf4j
public class DeckSaverService {
    private static final Set<Card> BASIC_LANDS = Set.of(
            new Card("Plains"),
            new Card("Island"),
            new Card("Mountain"),
            new Card("Swamp"),
            new Card("Forest"));
    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private CubeRepository cubeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void saveDecksFromDb(Set<Card> commanders, Path outputFolderPath, Set<Card> collection, int percentage, int maxLands, int monthsToLookBack) {
        deckRepository
                .findAllBySaveDateAfter(LocalDate.now().minusMonths(monthsToLookBack))
                .map(DeckEntity::toDeck)
                .filter(deck -> commanders.contains(deck.getCommander()))
                .filter(deck -> isAboveThreshold(deck, collection, percentage, maxLands))
                .forEach(deck -> saveDeck(outputFolderPath, deck, Utils.cardNameToFileName(deck.getCommander().name())));
    }

    public void saveAverageDecks(Set<? extends Cardholder> decks, Path outputFolderPath, Set<Card> collection, Integer averageDeckThreshold, int maxLands) {
        decks
                .stream()
                .filter(deck -> isAboveThreshold(deck, collection, averageDeckThreshold, maxLands))
                .forEach(deck -> saveDeck(outputFolderPath, deck, Utils.cardNameToFileName(deck.getCommander().name())));
    }

    @Transactional
    public void saveCubes(Path outputFolderPath, Map<Card, Integer> collection, int cubeThreshold, int monthsToLookBack, int popularCubeFollowerCount, int minCubeCardCount) {
        ZonedDateTime referenceDateTime = LocalDate.now().atStartOfDay().atZone(ZoneOffset.UTC).minusMonths(monthsToLookBack);
        log.info("Querying cubes from database in paged batches (monthsToLookBack: {}, minCubeCardCount: {})...", monthsToLookBack, minCubeCardCount);

        int pageSize = 1000;
        int pageNumber = 0;
        long totalSaved = 0;
        long totalProcessed = 0;

        Page<CubeEntity> page;
        do {
            page = cubeRepository.findAll(PageRequest.of(pageNumber, pageSize));
            pageNumber++;
            totalProcessed += page.getNumberOfElements();
            log.info("Processing cube batch {}/{} ({} cubes loaded so far)...", pageNumber, page.getTotalPages(), totalProcessed);

            for (CubeEntity entity : page.getContent()) {
                if (entity != null && (entity.getDateUpdated() == null || !entity.getDateUpdated().isBefore(referenceDateTime))) {
                    Cube cube = entity.toCube();
                    if (cube != null && cube.getCardsAndCounts() != null && getCubeTotalCardCount(cube) >= minCubeCardCount) {
                        if (isAboveThreshold(cube, collection, cubeThreshold)) {
                            saveCube(outputFolderPath, cube, popularCubeFollowerCount);
                            totalSaved++;
                        }
                    }
                }
            }
            entityManager.clear(); // Clear Hibernate first-level cache to prevent OutOfMemoryError
        } while (page.hasNext());

        log.info("Saved {}/{} cubes matching threshold (>= {}% owned, >= {} cards) to output folder.", totalSaved, totalProcessed, cubeThreshold, minCubeCardCount);
    }

    private long getCubeTotalCardCount(Cube cube) {
        if (cube == null || cube.getCardsAndCounts() == null) {
            return 0;
        }
        return cube.getCardsAndCounts().values().stream()
                .filter(count -> count != null)
                .mapToLong(Long::longValue)
                .sum();
    }

    private void saveCube(Path outputFolderPath, Cube cube, int popularCubeFollowerCount) {
        String folderName;
        int followerCount = cube.getFollowerCount();
        if (followerCount >= popularCubeFollowerCount) {
            folderName = "_cube" + SEPARATOR + "_popular";
        } else {
            folderName = "_cube";
        }
        String rawName = cube.getCubeName() != null ? Utils.cardNameToFileName(cube.getCubeName()).replaceAll("[^a-zA-Z0-9]", "") : "cube";
        if (rawName.isBlank()) {
            rawName = "cube";
        }
        String safeCubeName = rawName.length() > 50 ? rawName.substring(0, 50) : rawName;
        String fileName = outputFolderPath + SEPARATOR + folderName + SEPARATOR + cube.getPercentage() + "_" + safeCubeName + "_" + (cube.getId() != null ? cube.getId() : "") + ".txt";
        saveDeck(outputFolderPath, cube, folderName, fileName);
    }

    private void saveDeck(Path outputFolderPath, Cardholder cardHolder, String folderName) {
        String fileName = outputFolderPath + SEPARATOR + folderName + SEPARATOR + cardHolder.getPercentage() + "_" + Utils.cardNameToFileName(cardHolder.getTribe()) + "_" + Utils.cardNameToFileName(cardHolder.getIdentifier()) + ".txt";
        saveDeck(outputFolderPath, cardHolder, folderName, fileName);
    }

    private void saveDeck(Path outputFolderPath, Cardholder cardHolder, String folderName, String fileName) {
        Utils.createFolderIfNeeded(Path.of(outputFolderPath.toString(), folderName).toString());
        try (OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8.newEncoder())) {
            fileWriter.write(cardHolder.toFile());
        } catch (Exception e) {
            log.error("Something went wrong during file writing for {}", fileName, e);
        }
    }

    private boolean isAboveThreshold(Cube cube, Map<Card, Integer> collection, int cubeThreshold) {
        if (cube == null || cube.getCardsAndCounts() == null || cube.getCardsAndCounts().isEmpty()) {
            return false;
        }
        double cardsOwned = 0;
        double cardsNotOwned = 0;

        for (Map.Entry<Card, Long> entry : cube.getCardsAndCounts().entrySet()) {
            Card card = entry.getKey();
            Long neededCount = entry.getValue();
            if (card == null || neededCount == null) {
                continue;
            }
            if (collection.containsKey(card)) {
                Integer ownedCount = collection.get(card);
                if (ownedCount != null && neededCount > ownedCount) {
                    cardsOwned += ownedCount;
                    cardsNotOwned += neededCount - ownedCount;
                } else {
                    cardsOwned += neededCount;
                }
            } else {
                cardsNotOwned += neededCount;
            }
        }
        double totalCards = cardsOwned + cardsNotOwned;
        if (totalCards == 0) {
            return false;
        }
        double points = (cardsOwned / totalCards) * 100;
        cube.setPercentage((int) points);// Dirty (no command/query separation), but simple
        return points >= cubeThreshold;
    }

    private boolean isAboveThreshold(Cardholder cardHolder, Set<Card> collection, Integer percentage, Integer maxLands) {
        Set<Card> cards = cardHolder.getCards().stream().filter(card -> !BASIC_LANDS.contains(card)).collect(Collectors.toSet());
        int points = 99 - cards.size(); // Basically the amount of basic lands
        Card commander = cardHolder.getCommander();
        if (commander.isCombined()) {
            points--; // Since your 99 is a 98 with two commanders.
            if (collection.contains(commander.parts().getFirst())) {
                points++;
            }
            if (collection.contains(commander.parts().getSecond())) {
                points++;
            }
        }
        for (Card card : cards) {
            if (collection.contains(card)) {
                points++;
            }
        }
        cardHolder.setPercentage(points); // Dirty (no command/query separation), but simple
        int totalNumberOfCardsInDeck = cards.size() + (commander.isCombined() ? 2 : 1);
        return totalNumberOfCardsInDeck >= 100 - maxLands && points >= percentage;
    }
}
