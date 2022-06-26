package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EdhRecDeckScraper {
    private static String COMMANDER_REQUEST_TEMPLATE = "https://json.edhrec.com/v2/decks/%s.json";
    private static String DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/v2/deckpreview-temp/%s.json";

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private DeckRepository deckRepository;

    @Transactional
    public Map<Card, Set<Deck>> getCommandersToDecks(Set<Card> commanders) {
        // TODO multi-thread?
        Map<Card, Set<Deck>> commandersToDecks = new HashMap<>();
        for (Card commander : commanders) {
            ResponseEntity<EdhRecCommanderPage> commanderPageResponse = restTemplate.getForEntity(String.format(COMMANDER_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name())), EdhRecCommanderPage.class);
            Utils.sleep(200);
            List<DeckEntity> persistedDecks = deckRepository.findAllByCommander(commander.name());
            addDecksFromDb(commandersToDecks, commander, persistedDecks);
            if (commanderPageResponse.getStatusCode().is2xxSuccessful()) {
                Set<String> urlHashes = getUrlHashesToProcess(commander, persistedDecks, commanderPageResponse); // TODO update based on timestamp
                for (String urlHash : urlHashes) {
                    ResponseEntity<EdhRecDeck> deckResponse = restTemplate.getForEntity(String.format(DECK_REQUEST_TEMPLATE, urlHash), EdhRecDeck.class);
                    if (deckResponse.getStatusCode().is2xxSuccessful()) {
                        Deck deck = new Deck(commander, deckResponse.getBody().getCards().stream().map(Card::new).collect(Collectors.toSet()));
                        boolean addResult = commandersToDecks.computeIfAbsent(commander, key -> new HashSet<>()).add(deck);
                        if (!addResult) {
                            log.warn("Duplicate deck encountered from API, it will be persisted but won't be returned: {}", urlHash);
                        }
                        deckRepository.save(DeckEntity.fromDeck(urlHash, deck));
                        Utils.sleep(10);
                    } else {
                        log.error("EDHRec API deck request (commander: {}, deck: {}) returned an error: [{}] - {}", commander.name(), urlHash, commanderPageResponse.getStatusCodeValue(), commanderPageResponse.getStatusCode().getReasonPhrase());
                    }
                }
            } else {
                log.error("EDHRec API commander request ({}) returned an error: [{}] - {}", commander.name(), commanderPageResponse.getStatusCodeValue(), commanderPageResponse.getStatusCode().getReasonPhrase());
            }
        }
        return commandersToDecks;
    }

    private void addDecksFromDb(Map<Card, Set<Deck>> commandersToDecks, Card commander, List<DeckEntity> persistedDecks) {
        for (DeckEntity persistedDeck : persistedDecks) {
            Deck deckFromDb = persistedDeck.toDeck();
            boolean addResult = commandersToDecks.computeIfAbsent(commander, key -> new HashSet<>()).add(deckFromDb);
            if (!addResult) {
                log.warn("Duplicate deck encountered from DB, it will be persisted but won't be returned: {}", persistedDeck.getId());
            }
        }
    }

    private Set<String> getUrlHashesToProcess(Card commander, List<DeckEntity> persistedDecks, ResponseEntity<EdhRecCommanderPage> commanderPageResponse) {
        Set<String> urlHashes = commanderPageResponse.getBody().getTable().stream().map(EdhRecDeckId::getUrlHash).collect(Collectors.toSet());
        Set<String> persistedDeckIds = persistedDecks.stream().map(DeckEntity::getId).collect(Collectors.toSet());
        urlHashes.removeAll(persistedDeckIds);
        log.info("Skipping {} decks for commander {} due to them already being cached...", persistedDeckIds.size(), commander.name());
        return urlHashes;
    }

    @Data
    @NoArgsConstructor
    public static class EdhRecCommanderPage {
        private List<EdhRecDeckId> table;
    }

    @Data
    @NoArgsConstructor
    private static class EdhRecDeckId {
        @JsonProperty("urlhash")
        private String urlHash;
    }

    @Data
    @NoArgsConstructor
    private static class EdhRecDeck {
        private Set<String> cards;
    }
}
