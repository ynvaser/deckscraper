package systems.bdev.deckscraper.config;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import systems.bdev.deckscraper.model.Card;
import systems.bdev.deckscraper.persistence.ConfigEntity;
import systems.bdev.deckscraper.persistence.ConfigRepository;
import systems.bdev.deckscraper.util.CardDeserializer;
import systems.bdev.deckscraper.util.CardSerializer;
import systems.bdev.deckscraper.util.Utils;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Configuration
public class BeanConfig {
    @Bean
    public RestTemplate restTemplate()
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
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory();

        requestFactory.setHttpClient(httpClient);
        return new RestTemplate(requestFactory);
    }

    @Bean
    public DbxClientV2 createDbxClient(ConfigRepository configRepository) throws DbxException {
        ConfigEntity configEntity = configRepository.findById(0)
                .orElseThrow(() -> new RuntimeException("Please overwrite /database/h2db.mv.db with the one downloaded with the latest release of Deckscraper!"));
        String[] split = new String(Base64.getDecoder().decode(configEntity.getContent().getBytes(StandardCharsets.UTF_8))).split(Utils.SPECIAL_DELIMITER);
        DbxRequestConfig config = DbxRequestConfig.newBuilder(split[0]).build();
        DbxCredential dbxCredential = new DbxCredential(split[1], -1L, split[2], split[3], split[4]);
        DbxClientV2 dbxClientV2 = new DbxClientV2(config, dbxCredential);
        dbxClientV2.refreshAccessToken();
        return dbxClientV2;
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
