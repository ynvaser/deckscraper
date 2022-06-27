package systems.bdev.deckscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import javax.transaction.Transactional;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class DeckSaverService {
    @Autowired
    private DeckRepository deckRepository;

    @Transactional
    public void saveDecks(File jarLocation, Path outputFolderPath, Set<Card> collection, String[] args){
        deckRepository.findAllBy().map(DeckEntity::toDeck).filter(deck -> isAboveThreshold(deck, collection, Integer.parseInt(args[1]))).forEach(deck -> {
            String commanderFolderName = Utils.cardNameToJsonFileName(deck.getCommander().name());
            Utils.createFolderIfNeeded(jarLocation, "\\output\\" + commanderFolderName);
            String outputFileName = outputFolderPath + "\\" + commanderFolderName + "\\" + deck.getPercentage() + "_" + deck.hashCode() + ".txt";
            try (OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(outputFileName), StandardCharsets.UTF_8.newEncoder())) {
                fileWriter.write(deck.toFile());
            } catch (Exception e) {
                log.error("Something went wrong during file writing for {}", outputFileName, e);
            }
        });
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
}
