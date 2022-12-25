package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.AverageDeck;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.CardType;
import systems.bdev.deckscraper.model.Deck;
import systems.bdev.deckscraper.persistence.DeckEntity;
import systems.bdev.deckscraper.persistence.DeckRepository;
import systems.bdev.deckscraper.util.Utils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static systems.bdev.deckscraper.util.Utils.IS_NUMBER_REGEX;
import static systems.bdev.deckscraper.util.Utils.monthsBetween;

@Component
@Slf4j
public class EdhRecDeckScraper {
    private static String COMMANDER_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/decks/%s.json";
    private static String DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/deckpreview-temp/%s.json";
    private static String AVERAGE_DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/average-decks/%s.json";
    private static String AVERAGE_TRIBE_DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/average-decks/%s/%s.json";

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private DeckRepository deckRepository;

    public void persistCommandersAndDecks(Set<Card> commanders, int monthsToLookBack) {
        for (Card commander : commanders) {
            log.info("Started scraping for {}", commander.name());
            ResponseEntity<EdhRecCommanderPage> commanderPageResponse = getCommanderPageResponse(commander);
            Utils.sleep(200);
            List<DeckEntity> persistedDecksFresherThanMonthsToLookBack = deckRepository.findAllByCommander(commander.name());
            if (commanderPageResponse != null && commanderPageResponse.getBody() != null  && commanderPageResponse.getBody().getTable() != null) {
                Map<String, LocalDate> urlHashesWithSaveDates = getUrlHashesWithSaveDatesToProcess(commander, persistedDecksFresherThanMonthsToLookBack, commanderPageResponse, monthsToLookBack);
                urlHashesWithSaveDates.keySet().parallelStream().forEach(urlHash -> {
                            log.info("Pulling deck {} for commander {}", urlHash, commander.name());
                            ResponseEntity<EdhRecDeck> deckResponse = getDeck(urlHash);
                            if (deckResponse != null) {
                                Deck deck = new Deck(commander, deckResponse.getBody().getCards().stream().map(Card::new).collect(Collectors.toSet()));
                                deckRepository.saveAndFlush(DeckEntity.fromDeck(urlHash, deck, deckResponse.getBody().cardHash, urlHashesWithSaveDates.get(urlHash))); // TODO use cardhash meaningfully
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
                log.info("Fetching average decks of commander {}", commander.name());
                Map<String, String> tribesToDescriptions = createAverageDecksDescriptions(commander, averageDeck);
                tribesToDescriptions.forEach((tribe, description) -> {
                    String humanReadableDelimiter = "TCGplayer</a>";
                    description = description.substring(description.lastIndexOf(humanReadableDelimiter) + humanReadableDelimiter.length());
                    Map<Card, Long> cardsAndCounts = Arrays
                            .stream(description.split("\n"))
                            .filter(s -> !s.isBlank())
                            .map(String::trim)
                            .map(line -> Pair.of(line.replaceAll(IS_NUMBER_REGEX, "").trim(), line.split(" ")[0]))
                            .map(pair -> Pair.of(new Card(pair.getFirst()), Long.parseLong(pair.getSecond().matches(IS_NUMBER_REGEX) ? pair.getSecond() : "1")))
                            .filter(pair -> !(commander.equals(pair.getFirst()) ||
                                    (commander.isCombined() && (
                                            commander.parts().getFirst().equals(pair.getFirst()) ||
                                            commander.parts().getSecond().equals(pair.getFirst())))))
                            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, Long::sum));
                    averageDecks.add(new AverageDeck(commander, tribe.toLowerCase(Locale.ROOT), cardsAndCounts));
                });
            } else {
                log.error("Can't find average deck of commander {}", commander.name());
            }
        });
        return averageDecks;
    }

    private Map<String, String> createAverageDecksDescriptions(Card commander, ResponseEntity<AverageEdhRecDeck> averageDeck) {
        Map<String, String> tribesToDescriptions = new HashMap<>();
        AverageEdhRecDeck body = averageDeck.getBody();
        tribesToDescriptions.put("default", body.getDescription());
        addAverageTribalDecks(commander, tribesToDescriptions, body);
        return tribesToDescriptions;
    }

    private void addAverageTribalDecks(Card commander, Map<String, String> tribesToDescriptions, AverageEdhRecDeck body) {
        List<AverageEdhRecDeckTribe> tribes = new ArrayList<>();
        AverageEdhRecDeckPanel panels = body.getPanels();
        if (panels != null) {
            AverageEdhRecDeckTribeLinks tribeLinks = panels.getTribeLinks();
            if (tribeLinks != null) {
                List<AverageEdhRecDeckTribe> budget = tribeLinks.getBudget();
                if (budget != null) {
                    tribes.addAll(budget);
                }
                List<AverageEdhRecDeckTribe> themes = tribeLinks.getThemes();
                if (themes != null) {
                    tribes.addAll(themes);
                }
            }
        }
        log.info("Found the following average tribal decks for commander {}: [{}]", commander.name(), tribes.stream().map(AverageEdhRecDeckTribe::getValue).collect(Collectors.joining(", ")));
        for (AverageEdhRecDeckTribe tribe : tribes) {
            ResponseEntity<AverageEdhRecDeck> averageDeck = getAverageDeck(commander, tribe.getSuffix().replaceAll("/", ""));
            tribesToDescriptions.put(tribe.getValue(), averageDeck.getBody().getDescription());
        }
    }

    private ResponseEntity<AverageEdhRecDeck> getAverageDeck(Card commander) {
        return getAverageDeck(commander, null);
    }

    private ResponseEntity<AverageEdhRecDeck> getAverageDeck(Card commander, String tribe) {
        ResponseEntity<AverageEdhRecDeck> averageEdhRecDeckResponseEntity = null;
        try {
            if (tribe == null) {
                averageEdhRecDeckResponseEntity = restTemplate.getForEntity(String.format(AVERAGE_DECK_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name())), AverageEdhRecDeck.class);
            } else {
                averageEdhRecDeckResponseEntity = restTemplate.getForEntity(String.format(AVERAGE_TRIBE_DECK_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name()), tribe), AverageEdhRecDeck.class);
            }
        } catch (Exception e) {
            log.debug("Couldn't find average deck for commander by it's regular name: {}", commander.name());
            Utils.sleep(200);
            try {
                if (tribe == null) {
                    averageEdhRecDeckResponseEntity = restTemplate.getForEntity(String.format(AVERAGE_DECK_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name())), AverageEdhRecDeck.class);
                } else {
                    averageEdhRecDeckResponseEntity = restTemplate.getForEntity(String.format(AVERAGE_TRIBE_DECK_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name()), tribe), AverageEdhRecDeck.class);
                }
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
            log.debug("Couldn't find commander by it's regular name: {}", commander.name());
            Utils.sleep(200);
            try {
                commanderPageResponse = restTemplate.getForEntity(String.format(COMMANDER_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name())), EdhRecCommanderPage.class);
            } catch (Exception f) {
                log.error("Couldn't find commander by it's name without the part after '//': {}", commander.name());
            }
        }
        return commanderPageResponse;
    }

    private Map<String, LocalDate> getUrlHashesWithSaveDatesToProcess(Card commander, List<DeckEntity> persistedDecks, ResponseEntity<EdhRecCommanderPage> commanderPageResponse, int monthsToLookBack) {
        LocalDate today = LocalDate.now();
        EdhRecCommanderPage commanderPage = commanderPageResponse.getBody();
        Map<String, LocalDate> urlHashesToSaveDates = commanderPage
                .getTable()
                .stream()
                .filter(edhRecDeckId -> monthsBetween(edhRecDeckId.getSaveDate(), today) <= monthsToLookBack)
                .collect(Collectors.toConcurrentMap(EdhRecDeckId::getUrlHash, EdhRecDeckId::getSaveDate));
        log.info("Skipping {}/{} decks for commander {} due to time filtering ({} months)", commanderPage.getTable().size() - urlHashesToSaveDates.size(), commanderPage.getTable().size(), commander.name(), monthsToLookBack);
        if (persistedDecks.size() > 0) {
            int removedHashesCount = 0;
            for (DeckEntity persistedDeck : persistedDecks) {
                if (urlHashesToSaveDates.containsKey(persistedDeck.getId()) && !persistedDeck.getSaveDate().isBefore(urlHashesToSaveDates.get(persistedDeck.getId()))) {
                    urlHashesToSaveDates.remove(persistedDeck.getId());
                    removedHashesCount++;
                }
            }
            log.info("Skipping {} decks for commander {} due to them already being cached...", removedHashesCount, commander.name());
        } else {
            log.info("No persisted decks found for {}", commander.name());
        }
        return urlHashesToSaveDates;
    }

    @Data
    @NoArgsConstructor
    private static class EdhRecCommanderPage {
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
        private AverageEdhRecDeckPanel panels;
    }

    @Data
    @NoArgsConstructor
    private static class AverageEdhRecDeckPanel {
        @JsonProperty("tribelinks")
        private AverageEdhRecDeckTribeLinks tribeLinks;
    }

    @Data
    @NoArgsConstructor
    private static class AverageEdhRecDeckTribeLinks {
        private List<AverageEdhRecDeckTribe> budget;
        private List<AverageEdhRecDeckTribe> themes;
    }

    @Data
    @NoArgsConstructor
    private static class AverageEdhRecDeckTribe {
        @JsonProperty("href-suffix")
        private String suffix;
        private String value;
    }
}
