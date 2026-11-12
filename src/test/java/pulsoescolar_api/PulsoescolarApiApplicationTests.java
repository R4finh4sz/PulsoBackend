package pulsoescolar_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PulsoescolarApiApplicationTests {
	@Autowired ConfigurableApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.isActive()).isTrue();
	}

}
