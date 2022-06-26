package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ScryfallCommanderSearcher {
    private static final String COMMANDER_QUERY_STRING_FORMAT = "https://api.scryfall.com/cards/search?format=json&include_extras=false&include_multilingual=false&order=name&page=%d&q=is:commander&unique=cards";
    private static final List<String> TYPE_LINE_FILTER = List.of("Legendary Enchantment — Background"); // TODO EDHRec has no support for these as of now
    private static final List<String> KEYWORD_FILTER = List.of("Partner"); // TODO implement, FYI Friends Forever isn't a keyword

    @Autowired
    private RestTemplate restTemplate;

    public Set<Card> fetchCommanders() {
        Set<Card> result = new HashSet<>(2000);
        int page = 1;
        ResponseEntity<ScryfallResult> response;
        do {
            response = restTemplate.getForEntity(String.format(COMMANDER_QUERY_STRING_FORMAT, page++), ScryfallResult.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                ScryfallResult body = response.getBody();
                body.getData()
                        .stream()
                        .filter(scryfallCard -> !TYPE_LINE_FILTER.contains(scryfallCard.getTypeLine())
                                && scryfallCard.getKeywords().stream().noneMatch(KEYWORD_FILTER::contains))
                        .map(scryfallCard -> new Card(scryfallCard.getName()))
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
    }
}
