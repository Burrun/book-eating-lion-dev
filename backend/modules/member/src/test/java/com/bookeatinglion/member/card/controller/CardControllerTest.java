package com.bookeatinglion.member.card.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.card.domain.CardStatus;
import com.bookeatinglion.member.card.dto.CardResponse;
import com.bookeatinglion.member.card.exception.CardNotFoundException;
import com.bookeatinglion.member.card.exception.InvalidCardStatusChangeException;
import com.bookeatinglion.member.card.exception.UnauthorizedCardAccessException;
import com.bookeatinglion.member.card.service.CardService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {CardController.class, CardExceptionHandler.class})
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class CardControllerTest {

    private static final long MEMBER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardService cardService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject("member-sub-1").claim("member_id", MEMBER_ID));
    }

    private CardResponse cardResponse(CardStatus status) {
        return new CardResponse(
                1L,
                "5361-****-****-1234",
                status,
                1_000_000L,
                0L,
                LocalDate.now(),
                LocalDate.now().plusYears(3));
    }

    @Test
    void 카드_발급은_201과_데이터를_반환한다() throws Exception {
        when(cardService.issueCard(eq(MEMBER_ID), any())).thenReturn(cardResponse(CardStatus.ACTIVE));

        mockMvc.perform(post("/api/cards").with(authenticated()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedCardNumber").value("5361-****-****-1234"));
    }

    @Test
    void 내_카드_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(cardService.getMyCards(MEMBER_ID)).thenReturn(List.of(cardResponse(CardStatus.ACTIVE)));

        mockMvc.perform(get("/api/cards/me").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cardStatus").value("ACTIVE"));
    }

    @Test
    void 카드_상태_변경은_200과_데이터를_반환한다() throws Exception {
        when(cardService.changeStatus(MEMBER_ID, 1L, CardStatus.SUSPENDED))
                .thenReturn(cardResponse(CardStatus.SUSPENDED));

        mockMvc.perform(patch("/api/cards/1/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardStatus\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardStatus").value("SUSPENDED"));
    }

    @Test
    void 타인의_카드_상태변경은_403을_반환한다() throws Exception {
        when(cardService.changeStatus(MEMBER_ID, 1L, CardStatus.SUSPENDED))
                .thenThrow(new UnauthorizedCardAccessException(1L));

        mockMvc.perform(patch("/api/cards/1/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardStatus\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_카드_상태변경은_404를_반환한다() throws Exception {
        when(cardService.changeStatus(MEMBER_ID, 999L, CardStatus.SUSPENDED))
                .thenThrow(new CardNotFoundException(999L));

        mockMvc.perform(patch("/api/cards/999/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardStatus\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void CLOSED_카드_상태변경은_400을_반환한다() throws Exception {
        when(cardService.changeStatus(MEMBER_ID, 1L, CardStatus.ACTIVE))
                .thenThrow(new InvalidCardStatusChangeException(1L));

        mockMvc.perform(patch("/api/cards/1/status")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardStatus\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
