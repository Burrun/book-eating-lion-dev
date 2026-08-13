package com.bookeatinglion.catalog.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.port.RestockEmailSender.RestockEmail;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@ExtendWith(MockitoExtension.class)
class SesRestockEmailSenderTest {
    @Mock
    SesV2Client sesClient;

    @Mock
    TemplateEngine templateEngine;

    @Test
    void 타임리프_HTML을_SES_수신자에게_발송한다() throws Exception {
        when(templateEngine.process(any(String.class), any())).thenReturn("<html>재입고</html>");
        SesRestockEmailSender sender = new SesRestockEmailSender(sesClient, templateEngine);
        Field fromAddress = SesRestockEmailSender.class.getDeclaredField("fromAddress");
        fromAddress.setAccessible(true);
        fromAddress.set(sender, "verified-sender@example.com");

        sender.send(new RestockEmail(
                "verified-recipient@example.com", "사자", "책", "저자", null, "http://localhost:3000/books/1"));

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        assertThat(captor.getValue().destination().toAddresses()).containsExactly("verified-recipient@example.com");
        assertThat(captor.getValue().fromEmailAddress()).isEqualTo("verified-sender@example.com");
    }
}
