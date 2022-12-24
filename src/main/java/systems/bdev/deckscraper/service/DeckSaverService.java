package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Cardholder;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import javax.transaction.Transactional;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Transactional
    public void saveDecksFromDb(Set<Card> commanders, Path outputFolderPath, Set<Card> collection, int percentage, int maxLands, int monthsToLookBack) {
        deckRepository
                .findAllBySaveDateAfter(LocalDate.now().minusMonths(monthsToLookBack))
                .map(DeckEntity::toDeck)
                .filter(deck -> commanders.contains(deck.getCommander()))
                .filter(deck -> isAboveThreshold(deck, collection, percentage, maxLands))
                .forEach(deck -> saveDeck(outputFolderPath, deck, Utils.cardNameToJsonFileName(deck.getCommander().name())));
    }

    public void saveAverageDecks(Set<? extends Cardholder> decks, Path outputFolderPath, Set<Card> collection, Integer averageDeckThreshold) {
        decks
                .stream()
                .filter(deck -> isAboveThreshold(deck, collection, averageDeckThreshold, 99))
                .forEach(deck -> saveDeck(outputFolderPath, deck, Utils.cardNameToJsonFileName(deck.getCommander().name())));
    }

    private void saveDeck(Path outputFolderPath, Cardholder cardHolder, String commanderFolderName) {
        Utils.createFolderIfNeeded(Path.of(outputFolderPath.toString(), commanderFolderName).toString());
        String outputFileName = outputFolderPath + "\\" + commanderFolderName + "\\" + cardHolder.getPercentage() + "_" + cardHolder.getTribe() + "_" + cardHolder.hashCode() + ".txt";
        try (OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(outputFileName), StandardCharsets.UTF_8.newEncoder())) {
            fileWriter.write(cardHolder.toFile());
        } catch (Exception e) {
            log.error("Something went wrong during file writing for {}", outputFileName, e);
        }
    }

    private boolean isAboveThreshold(Cardholder cardHolder, Set<Card> collection, Integer percentage, Integer maxLands) {
        Set<Card> cards = cardHolder.getCardsAsSet().stream().filter(card -> !BASIC_LANDS.contains(card)).collect(Collectors.toSet());
        int points = 100 - cards.size(); //Max is 99, you already have the commander.
        for (Card card : cards) {
            if (collection.contains(card)) {
                points++;
            }
        }
        cardHolder.setPercentage(points); // Dirty (no command/query separation), but simple
        return cards.size() >= 100 - maxLands && points >= percentage;
    }
}
