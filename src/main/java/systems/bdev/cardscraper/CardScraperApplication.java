package systems.bdev.deckscraper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import systems.bdev.deckscraper.service.deckscraperService;

@SpringBootApplication
public class deckscraperApplication implements CommandLineRunner {
	@Autowired
	private deckscraperService deckscraperService;

	public static void main(String[] args) {
		ConfigurableApplicationContext applicationContext = SpringApplication.run(deckscraperApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		deckscraperService.run(args);
	}
}
