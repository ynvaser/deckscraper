package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.model.CardType;
import systems.bdev.deckscraper.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ScryfallService {
    private static final String COMMANDER_QUERY_STRING_FORMAT = "https://api.scryfall.com/cards/search?format=json&include_extras=false&include_multilingual=false&order=name&page=%d&q=is:commander&unique=cards";
    private static final String LAND_QUERY_STRING_FORMAT = "https://api.scryfall.com/cards/search?format=json&include_extras=false&include_multilingual=false&order=name&page=%d&unique=cards&q=(-type:artifact+-type:creature+-type:enchantment+-type:instant+-type:planeswalker+-type:sorcery)+type:land+-is:digital&unique=cards&order=name";
    private static final String FRIENDS_FOREVER_ORACLE_TEXT = "friends forever";
    private static final String CHOOSE_A_BACKGROUND_ORACLE_TEXT = "choose a background";
    private static final String BACKGROUND = "Background";
    private static final String PARTNER = "Partner";

    @Autowired
    private RestTemplate restTemplate;

    public Set<Card> fetchCommandersAndBackgrounds() {
        Set<Card> result = new HashSet<>(2000);
        int page = 1;
        ResponseEntity<ScryfallResult> response;
        do {
            response = restTemplate.getForEntity(String.format(COMMANDER_QUERY_STRING_FORMAT, page++), ScryfallResult.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                ScryfallResult body = response.getBody();
                body.getData()
                        .stream()
                        .map(scryfallCard -> {
                            String oracleTextInLowerCase = scryfallCard.getOracleText() == null ? "" : scryfallCard.getOracleText().toLowerCase(Locale.ROOT);
                            CardType cardType;
                            if (scryfallCard.getKeywords().contains(PARTNER) || scryfallCard.getKeywords().contains(PARTNER.toLowerCase(Locale.ROOT))) {
                                cardType = CardType.PARTNER;
                            }else if (oracleTextInLowerCase.contains(FRIENDS_FOREVER_ORACLE_TEXT)) {
                                cardType = CardType.FRIENDS_FOREVER;
                            } else if (oracleTextInLowerCase.contains(CHOOSE_A_BACKGROUND_ORACLE_TEXT)) {
                                cardType = CardType.CHOOSE_A_BACKGROUND;
                            } else if (scryfallCard.getTypeLine().contains(BACKGROUND)) {
                                cardType = CardType.BACKGROUND;
                            } else {
                                cardType = CardType.NORMAL;
                            }
                            return new Card(scryfallCard.getName(), null, cardType);
                        })
                        .collect(Collectors.toCollection(() -> result));
            } else {
                throw new RuntimeException("Scryfall returned an error: " + response.getStatusCode().getReasonPhrase());
            }
            Utils.sleep(200); // Scryfall has a request limit of 10 requests per second, doing half of that just to be safe.
        } while (response.getBody().getHasMore());

        return result;
    }

    public Set<Card> fetchAllLands() {
        Set<Card> result = new HashSet<>(2000);
        int page = 1;
        ResponseEntity<ScryfallResult> response;
        do {
            response = restTemplate.getForEntity(String.format(LAND_QUERY_STRING_FORMAT, page++), ScryfallResult.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                ScryfallResult body = response.getBody();
                body.getData()
                        .stream()
                        .map(scryfallCard -> new Card(scryfallCard.getName(), null, CardType.NORMAL))
                        .collect(Collectors.toCollection(() -> result));
            } else {
                throw new RuntimeException("Scryfall returned an error: " + response.getStatusCode().getReasonPhrase());
            }
            Utils.sleep(200); // Scryfall has a request limit of 10 requests per second, doing half of that just to be safe.
        } while (response.getBody().getHasMore());

        return result;
    }

    @NoArgsConstructor
    @Data
    public static class ScryfallResult {
        @JsonProperty("total_cards")
        private Integer totalCards;
        @JsonProperty("has_more")
        private Boolean hasMore;
        @JsonProperty("next_page")
        private String nextPage;
        private List<ScryfallCard> data;
    }

    @NoArgsConstructor
    @Data
    public static class ScryfallCard {
        private String name;
        @JsonProperty("type_line")
        private String typeLine;
        private List<String> keywords;
        @JsonProperty("oracle_text")
        private String oracleText;
    }
}
