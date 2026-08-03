package com.bookeatinglion.book;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.bookeatinglion.book")
@EntityScan(basePackages = {"com.bookeatinglion.book", "com.bookeatinglion.common"})
@EnableJpaRepositories(basePackages = "com.bookeatinglion.book")
@EnableJpaAuditing
public class BookModuleTestApplication {
}
