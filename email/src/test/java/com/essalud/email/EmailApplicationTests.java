package com.essalud.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Arranca el contexto sin Kafka ni buzón real: solo verifica que la configuración sea válida. */
@SpringBootTest(properties = {
        "app.mail.from=pruebas@samec.local",
        "spring.kafka.listener.auto-startup=false"
})
class EmailApplicationTests {

	@Test
	void contextLoads() {
	}

}
