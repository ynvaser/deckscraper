package systems.bdev.deckscraper.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Utils {
    public static String cardNameToJsonFileName(String cardName) {
        return cardName.toLowerCase().replaceAll("[,'/]", "").trim().replaceAll(" +", " ").replaceAll(" ", "-");
    }

    public static void sleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
