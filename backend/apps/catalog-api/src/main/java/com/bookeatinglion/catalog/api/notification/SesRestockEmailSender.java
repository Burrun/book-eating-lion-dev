package com.bookeatinglion.catalog.api.notification;

import com.bookeatinglion.book.port.RestockEmailSender;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Component
@RequiredArgsConstructor
public class SesRestockEmailSender implements RestockEmailSender {
    private final SesV2Client sesClient;
    private final TemplateEngine templateEngine;

    @Value("${notifications.email.from-address}")
    private String fromAddress;

    @Override
    public void send(RestockEmail email) {
        Context context = new Context(Locale.KOREAN);
        context.setVariable("recipientName", email.recipientName());
        context.setVariable("bookTitle", email.bookTitle());
        context.setVariable("author", email.author());
        context.setVariable("coverImageUrl", email.coverImageUrl());
        context.setVariable("bookDetailUrl", email.bookDetailUrl());
        String html = templateEngine.process("email/restock-notification", context);

        EmailContent content = EmailContent.builder()
                .simple(Message.builder()
                        .subject(Content.builder()
                                .data("[책 먹는 사자] 기다리신 도서가 재입고되었습니다")
                                .charset(StandardCharsets.UTF_8.name())
                                .build())
                        .body(Body.builder()
                                .html(Content.builder()
                                        .data(html)
                                        .charset(StandardCharsets.UTF_8.name())
                                        .build())
                                .build())
                        .build())
                .build();
        sesClient.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder()
                        .toAddresses(email.recipientEmail())
                        .build())
                .content(content)
                .build());
    }
}
