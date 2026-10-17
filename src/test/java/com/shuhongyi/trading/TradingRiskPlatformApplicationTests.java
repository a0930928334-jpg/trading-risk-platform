package com.shuhongyi.trading;

import com.shuhongyi.trading.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TradingRiskPlatformApplicationTests {

	@Test
	void contextLoads() {
	}

}
