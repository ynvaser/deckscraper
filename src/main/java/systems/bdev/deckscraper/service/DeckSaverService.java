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

import javax.transaction.Transactional;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

    @Transactional
    public void saveDecksFromDb(Set<Card> commanders, Path outputFolderPath, Set<Card> collection, int percentage, int maxLands, int monthsToLookBack) {
        deckRepository
                .findAllBySaveDateAfter(LocalDate.now().minusMonths(monthsToLookBack))
                .map(DeckEntity::toDeck)
                .filter(deck -> commanders.contains(deck.getCommander()))
                .filter(deck -> isAboveThreshold(deck, collection, percentage, maxLands))
                .forEach(deck -> saveDeck(outputFolderPath, deck, Utils.cardNameToJsonFileName(deck.getCommander().name())));
    }

    public void saveAverageDecks(Set<? extends Cardholder> decks, Path outputFolderPath, Set<Card> collection, Integer averageDeckThreshold, int maxLands) {
        decks
                .stream()
                .filter(deck -> isAboveThreshold(deck, collection, averageDeckThreshold, maxLands))
                .forEach(deck -> saveDeck(outputFolderPath, deck, Utils.cardNameToJsonFileName(deck.getCommander().name())));
    }

    @Transactional
    public void saveCubes(Path outputFolderPath, Map<Card, Integer> collection, int cubeThreshold, int monthsToLookBack, int popularCubeFollowerCount) {
        ZonedDateTime referenceDateTime = LocalDate.now().atStartOfDay().atZone(ZoneOffset.UTC).minusMonths(monthsToLookBack);
        cubeRepository.findAllByDateUpdatedAfter(referenceDateTime)
                .map(CubeEntity::toCube)
                .filter(cube -> !cube.getCardsAndCounts().isEmpty())
                .filter(cube -> isAboveThreshold(cube, collection, cubeThreshold))
                .forEach(cube -> saveCube(outputFolderPath, cube, popularCubeFollowerCount));
    }

    private void saveCube(Path outputFolderPath, Cube cube, int popularCubeFollowerCount) {
        String folderName;
        if (cube.getUsersFollowing().size() >= popularCubeFollowerCount) {
            folderName = "_cube" + SEPARATOR + "_popular";
        } else {
            folderName = "_cube";
        }
        String fileName = outputFolderPath + SEPARATOR + folderName + SEPARATOR + cube.getPercentage() + "_" + Utils.cardNameToJsonFileName(cube.getCubeName()).replaceAll("[^a-zA-Z0-9]", "") + "_" + cube.getId() + ".txt";
        saveDeck(outputFolderPath, cube, folderName, fileName);
    }

    private void saveDeck(Path outputFolderPath, Cardholder cardHolder, String folderName) {
        String fileName = outputFolderPath + SEPARATOR + folderName + SEPARATOR + cardHolder.getPercentage() + "_" + cardHolder.getTribe() + "_" + cardHolder.getIdentifier() + ".txt";
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
        double cardsOwned = 0;
        double cardsNotOwned = 0;

        for (Map.Entry<Card, Long> entry : cube.getCardsAndCounts().entrySet()) {
            Card card = entry.getKey();
            Long neededCount = entry.getValue();
            if (collection.containsKey(card)) {
                Integer ownedCount = collection.get(card);
                if (neededCount > ownedCount) {
                    cardsOwned += ownedCount;
                    cardsNotOwned += neededCount - ownedCount;
                } else {
                    cardsOwned += neededCount;
                }
            } else {
                cardsNotOwned += neededCount;
            }
        }
        double points = (cardsOwned / (cardsOwned + cardsNotOwned)) * 100;
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
