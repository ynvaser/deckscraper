package systems.bdev.deckscraper.input;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.Cube;
import systems.bdev.deckscraper.persistence.ConfigEntity;
import systems.bdev.deckscraper.persistence.ConfigRepository;
import systems.bdev.deckscraper.persistence.CubeEntity;
import systems.bdev.deckscraper.persistence.CubeRepository;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CubeCobraService {
    public static final String CUBE_EXPORTS_URL = "https://cubecobra-public.s3.us-east-2.amazonaws.com/export/cubes.json";
    public static final String INDEX_TO_ORACLE_MAP_URL = "https://cubecobra-public.s3.us-east-2.amazonaws.com/export/indexToOracleMap.json";
    public static final String SIMPLE_CARD_DICT_URL = "https://cubecobra-public.s3.us-east-2.amazonaws.com/export/simpleCardDict.json";

    private final RestTemplate restTemplate;
    private final CubeRepository cubeRepository;
    private final ConfigRepository configRepository;

    @Autowired
    @Qualifier("customObjectMapper")
    private ObjectMapper objectMapper;

    public void refreshCubeDatabase() {
        try {
            HttpHeaders headers = restTemplate.headForHeaders(CUBE_EXPORTS_URL);
            long lastModifiedMillis = headers.getLastModified();
            ZonedDateTime upstreamUpdateDateTime;
            if (lastModifiedMillis > 0) {
                upstreamUpdateDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModifiedMillis), ZoneId.of("UTC"));
            } else {
                upstreamUpdateDateTime = ZonedDateTime.now();
            }

            Pair<Boolean, ZonedDateTime> isStoredDataStaleAndUpstreamUpdateDateTime = getIsStoredDataStaleAndUpstreamUpdateDateTime(upstreamUpdateDateTime);
            Boolean isStoredDataStale = isStoredDataStaleAndUpstreamUpdateDateTime.getFirst();
            boolean hasNumericIndexes = containsNumericCardIndexes();

            if (isStoredDataStale || hasNumericIndexes) {
                if (hasNumericIndexes) {
                    log.info("Detected legacy unmapped numeric card indexes in database. Triggering full re-sync...");
                }
                log.info("Downloading index mapping and card dictionary from S3...");
                Map<Integer, String> indexToCardNameMap = fetchIndexToCardNameMap();

                log.info("Downloading Cube database export from {}...", CUBE_EXPORTS_URL);
                restTemplate.execute(CUBE_EXPORTS_URL, HttpMethod.GET, null, response -> {
                    try (InputStream inputStream = response.getBody();
                         JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
                        if (parser.nextToken() == JsonToken.START_ARRAY) {
                            List<CubeEntity> batch = new ArrayList<>();
                            while (parser.nextToken() == JsonToken.START_OBJECT) {
                                Cube cube = objectMapper.readValue(parser, Cube.class);
                                if (cube != null && cube.getId() != null) {
                                    batch.add(CubeEntity.fromCube(cube, indexToCardNameMap));
                                    if (batch.size() >= 1000) {
                                        cubeRepository.saveAllAndFlush(batch);
                                        batch.clear();
                                    }
                                }
                            }
                            if (!batch.isEmpty()) {
                                cubeRepository.saveAllAndFlush(batch);
                                batch.clear();
                            }
                        }
                    }
                    return null;
                });
                saveUpstreamUpdateDateTime(upstreamUpdateDateTime.toString());
                log.info("Cube database refresh completed successfully.");
            } else {
                log.info("Stored data is not stale, skipping Cube lookup...");
            }
        } catch (Exception e) {
            log.error("Error in CubeCobraService!", e);
            throw new RuntimeException(e);
        }
    }

    private Map<Integer, String> fetchIndexToCardNameMap() {
        Map<Integer, String> result = new HashMap<>();
        try {
            log.info("Fetching indexToOracleMap from {}...", INDEX_TO_ORACLE_MAP_URL);
            Map<String, String> indexToOracleMap = restTemplate.execute(INDEX_TO_ORACLE_MAP_URL, HttpMethod.GET, null, response -> {
                try (InputStream is = new java.io.BufferedInputStream(response.getBody())) {
                    return objectMapper.readValue(is, new TypeReference<Map<String, String>>() {});
                }
            });

            log.info("Fetching simpleCardDict from {}...", SIMPLE_CARD_DICT_URL);
            Map<String, JsonNode> simpleCardDict = restTemplate.execute(SIMPLE_CARD_DICT_URL, HttpMethod.GET, null, response -> {
                try (InputStream is = new java.io.BufferedInputStream(response.getBody())) {
                    return objectMapper.readValue(is, new TypeReference<Map<String, JsonNode>>() {});
                }
            });

            if (indexToOracleMap != null && simpleCardDict != null) {
                for (Map.Entry<String, String> entry : indexToOracleMap.entrySet()) {
                    try {
                        Integer index = Integer.parseInt(entry.getKey());
                        String oracleId = entry.getValue();
                        JsonNode cardNode = simpleCardDict.get(oracleId);
                        if (cardNode != null && cardNode.has("name")) {
                            String name = cardNode.get("name").asText();
                            if (name != null && !name.isBlank()) {
                                result.put(index, name.trim());
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            log.info("Successfully built index-to-card-name mapping with {} entries.", result.size());
        } catch (Exception e) {
            log.error("Failed to fetch index-to-card-name mapping tables!", e);
        }
        return result;
    }

    private boolean containsNumericCardIndexes() {
        try {
            Page<CubeEntity> sample = cubeRepository.findAll(PageRequest.of(0, 50));
            for (CubeEntity entity : sample.getContent()) {
                if (entity.getCards() != null && !entity.getCards().isEmpty()) {
                    for (String cardName : entity.getCards()) {
                        if (cardName != null && cardName.matches("^\\d+$")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private Pair<Boolean, ZonedDateTime> getIsStoredDataStaleAndUpstreamUpdateDateTime(ZonedDateTime upstreamUpdateDateTime) {
        Optional<ConfigEntity> maybeLastUpdateTime = configRepository.findById(1);
        if (maybeLastUpdateTime.isPresent()) {
            ZonedDateTime storedUpdateDateTime = ZonedDateTime.parse(maybeLastUpdateTime.get().getContent());
            return Pair.of(storedUpdateDateTime.isBefore(upstreamUpdateDateTime), upstreamUpdateDateTime);
        }
        return Pair.of(true, upstreamUpdateDateTime);
    }

    private void saveUpstreamUpdateDateTime(String upstreamUpdateDateTime) {
        Optional<ConfigEntity> maybeConfigEntity = configRepository.findById(1);
        ConfigEntity configEntity = maybeConfigEntity.orElse(new ConfigEntity());
        configEntity.setId(1);
        configEntity.setContent(upstreamUpdateDateTime);
        configRepository.saveAndFlush(configEntity);
    }
}
