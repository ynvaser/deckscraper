package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static systems.bdev.deckscraper.util.Utils.IS_NUMBER_REGEX;
import static systems.bdev.deckscraper.util.Utils.monthsBetween;

@Component
@Slf4j
public class EdhRecDeckScraper {
    private static final String NEXT_DATA_START_TAG = "<script id=\"__NEXT_DATA__\" type=\"application/json\">";
    private static final String NEXT_DATA_END_TAG = "</script>";

    private static String COMMANDER_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/decks/%s.json";
    private static String DECK_REQUEST_TEMPLATE = "https://edhrec.com/deckpreview/%s";
    private static String AVERAGE_DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/average-decks/%s.json";
    private static String AVERAGE_TRIBE_DECK_REQUEST_TEMPLATE = "https://json.edhrec.com/pages/average-decks/%s/%s.json";

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private DeckRepository deckRepository;
    @Autowired
    @Qualifier("customObjectMapper")
    private ObjectMapper objectMapper;

    public void persistCommandersAndDecks(Set<Card> commanders, int monthsToLookBack) {
        for (Card commander : commanders) {
            log.info("Started scraping for {}", commander.name());
            ResponseEntity<EdhRecCommanderPage> commanderPageResponse = getCommanderPageResponse(commander);
            Utils.sleep(200);
            List<DeckEntity> persistedDecks = deckRepository.findAllByCommander(commander.name());
            List<DeckEntity> decksToBePersisted = new CopyOnWriteArrayList<>();
            if (commanderPageResponse != null && commanderPageResponse.getBody() != null  && commanderPageResponse.getBody().getTable() != null) {
                Map<String, LocalDate> urlHashesWithSaveDates = getUrlHashesWithSaveDatesToProcess(commander, persistedDecks, commanderPageResponse, monthsToLookBack);
                urlHashesWithSaveDates.keySet().forEach(urlHash -> {
                            log.info("Pulling deck {} for commander {}", urlHash, commander.name());
                            ResponseEntity<EdhRecDeck> deckResponse = getDeck(urlHash);
                            if (deckResponse != null && deckResponse.getBody() != null) {
                                Deck deck = new Deck(commander, deckResponse.getBody().getCards().stream().map(Card::new).collect(Collectors.toSet()));
                                decksToBePersisted.add(DeckEntity.fromDeck(urlHash, deck, deckResponse.getBody().cardHash, urlHashesWithSaveDates.get(urlHash)));
                            } else {
                                log.error("EDHRec API deck request (commander: {}, deck: {}) returned an error.", commander.name(), urlHash);
                            }
                            Utils.sleep(100);
                        }
                );
                deckRepository.saveAllAndFlush(decksToBePersisted);
                log.info("Done with all decks for {}!!!", commander.name());
            } else {
                log.error("EDHRec API commander request ({}) returned an error.", commander.name());
            }
        }
    }

    public Set<AverageDeck> fetchAverageDecks(Set<Card> commanders) {
        Set<AverageDeck> averageDecks = ConcurrentHashMap.newKeySet();
        for (Card commander : commanders) {
            String jsonStr = getAverageDeckJson(commander, null);
            if (jsonStr != null) {
                log.info("Fetching average decks of commander {}", commander.name());
                Map<String, String> tribesToJsons = createAverageDecksJsons(commander, jsonStr);
                tribesToJsons.forEach((tribe, tribeJsonStr) -> {
                    try {
                        JsonNode root = objectMapper.readTree(tribeJsonStr);
                        Map<Card, Long> cardsAndCounts = parseAverageDeckCardsAndCounts(root, commander);
                        if (!cardsAndCounts.isEmpty()) {
                            averageDecks.add(new AverageDeck(commander, tribe.toLowerCase(Locale.ROOT), cardsAndCounts));
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse average deck JSON for commander {} tribe {}", commander.name(), tribe, e);
                    }
                });
            } else {
                log.error("Can't find average deck of commander {}", commander.name());
            }
        }
        return averageDecks;
    }

    private Map<String, String> createAverageDecksJsons(Card commander, String defaultJsonStr) {
        Map<String, String> tribesToJsons = new HashMap<>();
        tribesToJsons.put("default", defaultJsonStr);
        try {
            JsonNode root = objectMapper.readTree(defaultJsonStr);
            JsonNode tagLinks = root.path("panels").path("taglinks");
            if (tagLinks.isArray()) {
                for (JsonNode tag : tagLinks) {
                    String slug = tag.path("slug").asText().replaceAll("/", "").trim();
                    String value = tag.path("value").asText().trim();
                    if (StringUtils.isNotBlank(slug) && StringUtils.isNotBlank(value)) {
                        String tribeJsonStr = getAverageDeckJson(commander, slug);
                        if (tribeJsonStr != null) {
                            tribesToJsons.put(value, tribeJsonStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Couldn't parse tribal panels for average deck {}", commander.name(), e);
        }
        return tribesToJsons;
    }

    private Map<Card, Long> parseAverageDeckCardsAndCounts(JsonNode root, Card commander) {
        Map<Card, Long> cardsAndCounts = new HashMap<>();

        // Strategy A: container.json_dict.cardlists
        JsonNode cardListsNode = root.path("container").path("json_dict").path("cardlists");
        if (cardListsNode.isArray() && cardListsNode.size() > 0) {
            for (JsonNode category : cardListsNode) {
                JsonNode cardViews = category.path("cardviews");
                if (cardViews.isArray()) {
                    for (JsonNode cv : cardViews) {
                        String name = cv.path("name").asText().trim();
                        if (StringUtils.isNotBlank(name)) {
                            long count = 1L;
                            String label = cv.path("label").asText().trim();
                            if (label.matches("^\\d+\\s+.*")) {
                                String qtyStr = label.split("\\s+")[0];
                                try {
                                    count = Long.parseLong(qtyStr);
                                } catch (NumberFormatException ignored) {}
                            }
                            Card card = new Card(name);
                            if (!isCommanderCard(card, commander)) {
                                cardsAndCounts.merge(card, count, Long::sum);
                            }
                        }
                    }
                }
            }
        }

        // Strategy B: deck.cards (map of category -> list of [cardName, count])
        if (cardsAndCounts.isEmpty()) {
            JsonNode cardsNode = root.path("deck").path("cards");
            if (cardsNode.isObject()) {
                cardsNode.fields().forEachRemaining(entry -> {
                    JsonNode categoryList = entry.getValue();
                    if (categoryList.isArray()) {
                        for (JsonNode item : categoryList) {
                            if (item.isArray() && item.size() >= 1) {
                                String name = item.get(0).asText().trim();
                                long count = 1L;
                                if (item.size() >= 2) {
                                    count = item.get(1).asLong(1L);
                                }
                                if (StringUtils.isNotBlank(name)) {
                                    Card card = new Card(name);
                                    if (!isCommanderCard(card, commander)) {
                                        cardsAndCounts.merge(card, count, Long::sum);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }

        // Strategy C: Legacy description text string parsing
        if (cardsAndCounts.isEmpty()) {
            String description = root.path("description").asText("");
            String humanReadableDelimiter = "TCGplayer</a>";
            if (description != null && description.contains(humanReadableDelimiter)) {
                description = description.substring(description.lastIndexOf(humanReadableDelimiter) + humanReadableDelimiter.length());
            }
            if (StringUtils.isNotBlank(description)) {
                Arrays.stream(description.split("\n"))
                        .filter(s -> !s.isBlank())
                        .map(String::trim)
                        .map(line -> Pair.of(line.replaceAll(IS_NUMBER_REGEX, "").trim(), line.split(" ")[0]))
                        .map(pair -> Pair.of(new Card(pair.getFirst()), Long.parseLong(pair.getSecond().matches(IS_NUMBER_REGEX) ? pair.getSecond() : "1")))
                        .filter(pair -> !isCommanderCard(pair.getFirst(), commander))
                        .forEach(pair -> cardsAndCounts.merge(pair.getFirst(), pair.getSecond(), Long::sum));
            }
        }

        return cardsAndCounts;
    }

    private boolean isCommanderCard(Card card, Card commander) {
        return commander.equals(card) ||
                (commander.isCombined() && (
                        commander.parts().getFirst().equals(card) ||
                        commander.parts().getSecond().equals(card)));
    }

    private String getAverageDeckJson(Card commander, String tribe) {
        try {
            String url = (tribe == null)
                    ? String.format(AVERAGE_DECK_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name()))
                    : String.format(AVERAGE_TRIBE_DECK_REQUEST_TEMPLATE, Utils.cardNameToJsonFileName(commander.name()), tribe);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.debug("Couldn't find average deck by regular name: {}", commander.name());
            Utils.sleep(200);
            try {
                String url = (tribe == null)
                        ? String.format(AVERAGE_DECK_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name()))
                        : String.format(AVERAGE_TRIBE_DECK_REQUEST_TEMPLATE, Utils.cardNameWithoutBacksideFileName(commander.name()), tribe);
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody();
                }
            } catch (Exception f) {
                log.warn("Couldn't find average deck for [{}] and tribe [{}]", commander.name(), tribe);
            }
        }
        return null;
    }

    private ResponseEntity<EdhRecDeck> getDeck(String urlHash) {
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Utils.sleep(250);
                ResponseEntity<String> response = restTemplate.getForEntity(String.format(DECK_REQUEST_TEMPLATE, urlHash), String.class);
                if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Set<String> cards = extractCardsFromNextDataHtml(response.getBody());
                    if (!cards.isEmpty()) {
                        EdhRecDeck deck = new EdhRecDeck();
                        deck.setCards(cards);
                        deck.setCardHash(urlHash);
                        return ResponseEntity.ok(deck);
                    }
                }
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed fetching deck {}: {}", attempt, maxRetries, urlHash, e.getMessage());
                Utils.sleep(500);
            }
        }
        log.info("Couldn't fetch deck: {}", urlHash);
        return null;
    }

    Set<String> extractCardsFromNextDataHtml(String html) {
        Set<String> cards = new HashSet<>();
        int startIndex = html.indexOf(NEXT_DATA_START_TAG);
        if (startIndex == -1) {
            return cards;
        }
        startIndex += NEXT_DATA_START_TAG.length();
        int endIndex = html.indexOf(NEXT_DATA_END_TAG, startIndex);
        if (endIndex == -1) {
            return cards;
        }

        String jsonString = html.substring(startIndex, endIndex);
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode dataNode = root.path("props").path("pageProps").path("data");

            // Primary source: data.deck (array of strings e.g. "1 Card Name")
            JsonNode deckArray = dataNode.path("deck");
            if (deckArray.isArray() && deckArray.size() > 0) {
                for (JsonNode cardLine : deckArray) {
                    String line = cardLine.asText().trim();
                    String cardName = line.replaceAll("^[0-9]+\\s+", "").trim();
                    if (StringUtils.isNotBlank(cardName)) {
                        cards.add(cardName);
                    }
                }
            }

            // Secondary fallback: data.container.json_dict.cardlist
            if (cards.isEmpty()) {
                JsonNode cardListNode = dataNode.path("container").path("json_dict").path("cardlist");
                if (cardListNode.isArray() && cardListNode.size() > 0) {
                    for (JsonNode category : cardListNode) {
                        JsonNode cardViews = category.path("cardviews");
                        if (cardViews.isArray()) {
                            for (JsonNode cardView : cardViews) {
                                String name = cardView.path("name").asText();
                                if (StringUtils.isNotBlank(name)) {
                                    cards.add(name);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse __NEXT_DATA__ JSON from EDHREC HTML", e);
        }
        return cards;
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
