package com.bookeatinglion.usedbook;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.bookeatinglion.usedbook",
        "com.bookeatinglion.isbn",
        "com.bookeatinglion.s3"
})
public class UsedBookModuleTestApplication {
}
