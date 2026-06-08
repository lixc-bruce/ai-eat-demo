package com.eat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires MySQL and Redis - run manually when infrastructure is available")
class EatApplicationTests {

    @Test
    void contextLoads() {
    }
}
