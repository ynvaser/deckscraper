package systems.bdev.deckscraper.input;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import systems.bdev.deckscraper.model.Cube;
import systems.bdev.deckscraper.persistence.ConfigEntity;
import systems.bdev.deckscraper.persistence.ConfigRepository;
import systems.bdev.deckscraper.persistence.CubeEntity;
import systems.bdev.deckscraper.persistence.CubeRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CubeCobraService {
    private static final TypeReference<List<Cube>> CUBE_LIST_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final DbxClientV2 dbxClient;
    private final CubeRepository cubeRepository;
    private final ConfigRepository configRepository;

    @Autowired
    @Qualifier("customObjectMapper")
    private ObjectMapper objectMapper;

    public void refreshCubeDatabase() {
        try {
            Metadata manifestMetadata = dbxClient.files().getMetadata("/cube_exports/manifest.7z");
            if (manifestMetadata != null) {
                Pair<Boolean, ZonedDateTime> isStoredDataStaleAndUpstreamUpdateDateTime = getIsStoredDataStaleAndUpstreamUpdateDateTime(manifestMetadata);
                Boolean isStoredDataStale = isStoredDataStaleAndUpstreamUpdateDateTime.getFirst();
                String upstreamUpdateDateTime = isStoredDataStaleAndUpstreamUpdateDateTime.getSecond().toString();
                if (isStoredDataStale) {
                    ListFolderResult folderContents = dbxClient.files().listFolder("/cube_exports/");
                    for (Metadata remoteFile : folderContents.getEntries()) {
                        if (!remoteFile.getName().contains("manifest")) {
                            List<String> fileContents = fetchFileContents(remoteFile);
                            for (String fileContent : fileContents) {
                                List<Cube> cubes = objectMapper.readValue(fileContent, CUBE_LIST_TYPE_REFERENCE);
                                List<CubeEntity> entities = cubes.stream().map(cube -> {
                                    log.info("Loading cube {} (ID: {})", cube.getCubeName(), cube.getId());
                                    return CubeEntity.fromCube(cube);
                                }).collect(Collectors.toList());
                                cubeRepository.saveAllAndFlush(entities);
                            }
                        } else {
                            log.info("Skipping manifest...");
                        }
                    }
                    saveUpstreamUpdateDateTime(upstreamUpdateDateTime);
                } else {
                    log.info("Stored data is not stale, skipping Cube lookup...");
                }
            }
        } catch (DbxException | IOException e) {
            log.error("Error in CubeCobraService!", e);
            throw new RuntimeException(e);
        }
    }

    private void saveUpstreamUpdateDateTime(String upstreamUpdateDateTime) {
        Optional<ConfigEntity> maybeConfigEntity = configRepository.findById(1);
        ConfigEntity configEntity = maybeConfigEntity.orElse(new ConfigEntity());
        configEntity.setId(1);
        configEntity.setContent(upstreamUpdateDateTime);
        configRepository.saveAndFlush(configEntity);
    }

    private Pair<Boolean, ZonedDateTime> getIsStoredDataStaleAndUpstreamUpdateDateTime(Metadata manifestMetadata) throws JsonProcessingException {
        String manifestContents = fetchFileContents(manifestMetadata).get(0);
        ObjectNode jsonNodes = objectMapper.readValue(manifestContents, ObjectNode.class);
        ZonedDateTime upstreamUpdateDateTime = ZonedDateTime.parse(jsonNodes.get("date_exported").asText());
        Optional<ConfigEntity> maybeLastUpdateTime = configRepository.findById(1);
        if (maybeLastUpdateTime.isPresent()) {
            ZonedDateTime storedUpdateDateTime = ZonedDateTime.parse(maybeLastUpdateTime.get().getContent());
            return Pair.of(storedUpdateDateTime.isBefore(upstreamUpdateDateTime), upstreamUpdateDateTime);
        }
        return Pair.of(true, upstreamUpdateDateTime);
    }

    private List<String> fetchFileContents(Metadata remoteFile) {
        List<String> result = new ArrayList<>();
        ByteArrayOutputStream bos = null;
        SevenZFile sevenZFile = null;
        SeekableInMemoryByteChannel seekableInMemoryByteChannel = null;
        try {
            bos = new ByteArrayOutputStream();
            dbxClient.files().download(remoteFile.getPathLower()).download(bos);
            seekableInMemoryByteChannel = new SeekableInMemoryByteChannel(bos.toByteArray());
            sevenZFile = new SevenZFile(seekableInMemoryByteChannel);
            SevenZArchiveEntry entry = sevenZFile.getNextEntry();
            while (entry != null) {
                long size = entry.getSize();
                byte[] data = new byte[(int) size];
                sevenZFile.read(data, 0, data.length);
                result.add(new String(data, StandardCharsets.UTF_8));
                entry = sevenZFile.getNextEntry();
            }
        } catch (DbxException | IOException e) {
            log.error("Error fetching file contents! Filename: {}", remoteFile.getName(), e);
            throw new RuntimeException(e);
        } finally {
            closeResources(bos, sevenZFile, seekableInMemoryByteChannel);
        }
        return result;
    }

    private void closeResources(ByteArrayOutputStream bos, SevenZFile sevenZFile, SeekableInMemoryByteChannel seekableInMemoryByteChannel) {
        if (bos != null) {
            try {
                bos.close();
            } catch (Exception e) {
                log.info("bos already closed");
            }
        }
        if (sevenZFile != null) {
            try {
                sevenZFile.close();
            } catch (IOException e) {
                log.info("sevenZFile already closed");
            }
        }
        if (seekableInMemoryByteChannel != null && seekableInMemoryByteChannel.isOpen()) {
            seekableInMemoryByteChannel.close();
        }
    }
}
