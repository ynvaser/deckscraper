package systems.bdev.deckscraper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import systems.bdev.deckscraper.service.DeckScraperService;

@SpringBootApplication
public class DeckScraperApplication implements CommandLineRunner {
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
    private DeckScraperService deckScraperService;

    public static void main(String[] args) {
        SpringApplication.run(DeckScraperApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        deckScraperService.run(args);
		SpringApplication.exit(applicationContext);
    }
}
