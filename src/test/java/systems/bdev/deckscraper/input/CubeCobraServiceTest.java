package systems.bdev.deckscraper.input;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CubeCobraServiceTest {
    @Autowired
    private CubeCobraService underTest;

    @Test
    void shouldFindCubes() {
        underTest.refreshCubeDatabase();
    }
}