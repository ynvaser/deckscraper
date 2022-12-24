package systems.bdev.deckscraper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import systems.bdev.deckscraper.service.DeckScraperService;
import systems.bdev.deckscraper.util.Utils;

@SpringBootApplication
@Slf4j
public class DeckScraperApplication {
    public static void main(String[] args) {
        log.info("Application started, please be patient...");
        ConfigurableApplicationContext applicationContext = SpringApplication.run(DeckScraperApplication.class, args);
        applicationContext.getBean(DeckScraperService.class).run(args);
        log.info("Application finished, please check the output folder for results.");
        SpringApplication.exit(applicationContext);
    }
}
