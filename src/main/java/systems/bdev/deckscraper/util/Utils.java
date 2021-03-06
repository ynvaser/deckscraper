package systems.bdev.deckscraper.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class Utils {
    public static String cardNameToJsonFileName(String cardName) {
        return cardName.toLowerCase().replaceAll("[,'/]", "").trim().replaceAll(" +", " ").replaceAll(" ", "-");
    }

    public static String cardNameWithoutBacksideFileName(String cardName) {
        return cardNameToJsonFileName(cardName.substring(0, cardName.indexOf('/')));
    }

    public static void sleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path createFolderIfNeeded(File jarLocation, String folderName) {
        return createFolderIfNeeded(jarLocation.getPath() + folderName);
    }

    public static Path createFolderIfNeeded(String fullFolderPath) {
        try {
            File inputFolder = new File(fullFolderPath);
            Path path = inputFolder.toPath();
            if (!inputFolder.exists()) {
                log.info("Folder \"{}\" doesn't exist, creating...", path);
                Files.createDirectories(path);
            } else {
                log.info("Folder \"{}\" exists, skipping creation...", path);
            }
            return path;
        } catch (Exception e) {
            log.error("File writing fail {}", fullFolderPath, e);
            throw new RuntimeException(e);
        }
    }
}
