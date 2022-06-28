package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.AverageDeck;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class EdhRecDeckScraper {
    private static String COMMANDER_REQUEST_TEMPLATE = "https://json.edhrec.com/v2/decks/%s.json";
    private static String DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/v2/deckpreview-temp/%s.json";
    private static String AVERAGE_DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/v2/average-decks/%s.json";

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private DeckRepository deckRepository;

    public void persistCommandersAndDecks(Set<Card> commanders, int monthsToLookBack) {
        for (Card commander : commanders) {
            log.info("Started scraping for {}", commander.name());
            ResponseEntity<EdhRecCommanderPage> commanderPageResponse = getCommanderPageResponse(commander);
            Utils.sleep(200);
            List<DeckEntity> persistedDecks = deckRepository.findAllByCommander(commander.name());
            if (commanderPageResponse != null) {
                Set<String> urlHashes = getUrlHashesToProcess(commander, persistedDecks, commanderPageResponse, monthsToLookBack); // TODO update based on timestamp
                urlHashes.parallelStream().forEach(urlHash -> {
                            log.info("Pulling deck {} for commander {}", urlHash, commander.name());
                            ResponseEntity<EdhRecDeck> deckResponse = getDeck(urlHash);
                            if (deckResponse != null) {
                                Deck deck = new Deck(commander, deckResponse.getBody().getCards().stream().map(Card::new).collect(Collectors.toSet()));
                                deckRepository.saveAndFlush(DeckEntity.fromDeck(urlHash, deck, deckResponse.getBody().cardHash)); // TODO use cardhash meaningfully
                                Utils.sleep(200);
                            } else {
                                log.error("EDHRec API deck request (commander: {}, deck: {}) returned an error.", commander.name(), urlHash);
                            }
                        }
                );
                log.info("Done with all decks for {}!!!", commander.name());
            } else {
                log.error("EDHRec API commander request ({}) returned an error.", commander.name());
            }
        }
    }

    public Set<AverageDeck> fetchAverageDecks(Set<Card> commanders) {
        Set<AverageDeck> averageDecks = ConcurrentHashMap.newKeySet();
        commanders.parallelStream().forEach(commander -> {
            ResponseEntity<AverageEdhRecDeck> averageDeck = getAverageDeck(commander);
            if (averageDeck != null) {
                String description = averageDeck.getBody().getDescription();
                String humanReadableDelimiter = "TCGplayer</a>";
                description = description.substring(description.lastIndexOf(humanReadableDelimiter) + humanReadableDelimiter.length());
                description = description.replaceAll("[123456789]", "");
                Map<Card, Long> cardsAndCounts = Arrays.stream(description.split("\n")).filter(s -> !s.isBlank()).map(String::trim).map(Card::new).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                averageDecks.add(new AverageDeck(commander, cardsAndCounts));
            } else {
                log.error("Can't find average deck of commander {}", commander.name());
            }
        });
        return averageDecks;
    }

    private ResponseEntity<AverageEdhRecDeck> getAverageDeck(Card commander) {
        ResponseEntity<AverageEdhRecDeck> averageEdhRecDeckResponseEntity = null;
        try {
            averageEdhRecDeckResponseEntity = restTemplate.getForEntity(String.format(AVERAGE_DECK_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name())), AverageEdhRecDeck.class);
        } catch (Exception e) {
            log.warn("Couldn't find average deck for commander by it's regular name: {}", commander.name());
            Utils.sleep(200);
            try {
                averageEdhRecDeckResponseEntity = restTemplate.getForEntity(String.format(AVERAGE_DECK_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name())), AverageEdhRecDeck.class);
            } catch (Exception f) {
                log.error("Couldn't find average deck for commander by it's name without the part after '//': {}", commander.name());
            }
        }
        return averageEdhRecDeckResponseEntity;
    }

    private ResponseEntity<EdhRecDeck> getDeck(String urlHash) {
        ResponseEntity<EdhRecDeck> response = null;
        try {
            response = restTemplate.getForEntity(String.format(DECK_REQUEST_TEMPLATE, urlHash), EdhRecDeck.class);
        } catch (Exception e) {
            log.info("Couldn't fetch deck: {}", urlHash);
        }
        return response;
    }

    private ResponseEntity<EdhRecCommanderPage> getCommanderPageResponse(Card commander) {
        ResponseEntity<EdhRecCommanderPage> commanderPageResponse = null;
        try {
            commanderPageResponse = restTemplate.getForEntity(String.format(COMMANDER_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name())), EdhRecCommanderPage.class);
        } catch (Exception e) {
            log.warn("Couldn't find commander by it's regular name: {}", commander.name());
            Utils.sleep(200);
            try {
                commanderPageResponse = restTemplate.getForEntity(String.format(COMMANDER_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name())), EdhRecCommanderPage.class);
            } catch (Exception f) {
                log.error("Couldn't find commander by it's name without the part after '//': {}", commander.name());
            }
        }
        return commanderPageResponse;
    }

    private Set<String> getUrlHashesToProcess(Card commander, List<DeckEntity> persistedDecks, ResponseEntity<EdhRecCommanderPage> commanderPageResponse, int monthsToLookBack) {
        LocalDate today = LocalDate.now();
        EdhRecCommanderPage commanderPage = commanderPageResponse.getBody();
        Set<String> urlHashes = ConcurrentHashMap.newKeySet();
        commanderPage
                .getTable()
                .stream()
                .filter(edhRecDeckId -> monthsBetween(edhRecDeckId.getSaveDate(), today) <= monthsToLookBack)
                .map(EdhRecDeckId::getUrlHash)
                .collect(Collectors.toCollection(() -> urlHashes));
        log.info("Skipping {}/{} decks for commander {} due to time filtering ({} months)", commanderPage.getTable().size() - urlHashes.size(), commanderPage.getTable().size(), commander.name(), monthsToLookBack);
        Set<String> persistedDeckIds = persistedDecks.stream().map(DeckEntity::getId).collect(Collectors.toSet());
        if (persistedDecks.size() > 0) {
            urlHashes.removeAll(persistedDeckIds);
            log.info("Skipping {} decks for commander {} due to them already being cached...", persistedDeckIds.size(), commander.name());
        } else {
            log.info("No persisted decks found for {}", commander.name());
        }
        return urlHashes;
    }

    private long monthsBetween(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            log.error("Supplied date is null!");
        }
        return ChronoUnit.MONTHS.between(
                a.withDayOfMonth(1),
                b.withDayOfMonth(1));
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
        @JsonProperty("savedate")
        private LocalDate saveDate;
    }

    @Data
    @NoArgsConstructor
    private static class EdhRecDeck {
        @JsonProperty("cardhash")
        private String cardHash;
        private Set<String> cards;
    }

    @Data
    @NoArgsConstructor
    private static class AverageEdhRecDeck {
        private String description;
    }
}
