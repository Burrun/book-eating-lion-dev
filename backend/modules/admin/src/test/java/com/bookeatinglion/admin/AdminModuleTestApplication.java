package com.bookeatinglion.admin;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication(scanBasePackages = "com.bookeatinglion.admin")
@EnableMethodSecurity
public class AdminModuleTestApplication {
}
