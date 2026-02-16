package com.roadrats.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.cls.url=jdbc:h2:mem:testdb",
    "spring.datasource.cls.driver-class-name=org.h2.Driver",
    "spring.datasource.io.url=jdbc:h2:mem:testdb2",
    "spring.datasource.io.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RoadratsBackendApplicationTests {

	@Test
	void contextLoads() {
		// Test that the application context loads successfully
	}

}
