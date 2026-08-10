package systems.bdev.deckscraper.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.util.CardDeserializer;
import systems.bdev.deckscraper.util.CardSerializer;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@Slf4j
public class BeanConfig {

    private HttpComponentsClientHttpRequestFactory createRequestFactory()
            throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();

        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(2000);
        connectionManager.setDefaultMaxPerRoute(2000);

        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setSSLSocketFactory(csf)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory();

        requestFactory.setHttpClient(httpClient);
        return requestFactory;
    }

    @Bean
    public RestTemplate restTemplate()
            throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        return new RestTemplate(createRequestFactory());
    }

    private static final long MIN_SCRYFALL_INTERVAL_MS = 550; // Scryfall /cards/search limit is 2/sec (500ms). 550ms is safely below the limit.
    private static final AtomicLong nextAllowedScryfallTimeMs = new AtomicLong(0);

    private static void enforceScryfallRateLimit() {
        long now = System.currentTimeMillis();
        long scheduledTime = nextAllowedScryfallTimeMs.getAndUpdate(last ->
                Math.max(now, last) + MIN_SCRYFALL_INTERVAL_MS
        );
        long sleepMs = scheduledTime - now;
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Bean("scryfallRestTemplate")
    public RestTemplate scryfallRestTemplate()
            throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        RestTemplate template = new RestTemplate(createRequestFactory());
        template.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("User-Agent", "Deckscraper/1.0 (https://github.com/deckscraper)");
            request.getHeaders().set("Accept", "application/json, */*");
            enforceScryfallRateLimit();
            return execution.execute(request, body);
        });
        return template;
    }

    @Bean("customObjectMapper")
    public ObjectMapper customObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Card.class, new CardSerializer());
        module.addDeserializer(Card.class, new CardDeserializer());
        objectMapper.registerModule(module);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }
}
