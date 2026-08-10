package com.bookeatinglion.ai.bot.controller;

import com.bookeatinglion.ai.bot.domain.Inquiry;
import com.bookeatinglion.ai.bot.service.InquiryBotService;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/ai/bot/** → Ingress 가 ai-bot Deployment 로 라우팅한다. */
@RestController
@RequestMapping("/api/ai/bot")
@RequiredArgsConstructor
public class BotController {

    private final InquiryBotService inquiryBotService;

    public record AskRequest(@NotBlank String question) {}

    public record InquiryRequest(Long bookId, String bookTitle, @NotBlank String title, @NotBlank String content) {}

    @PostMapping("/ask")
    public ApiResponse<String> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.success(inquiryBotService.answer(request.question()));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/inquiries")
    public ApiResponse<Long> submit(@Valid @RequestBody InquiryRequest request) {
        // memberId·nickname 모두 JWT 클레임에서 온다. member-service 호출 없음.
        Inquiry inquiry = new Inquiry(
                SecurityUtils.currentMemberId(),
                SecurityUtils.currentNickname(),
                request.bookId(),
                request.bookTitle(),
                request.title(),
                request.content());

        return ApiResponse.success(inquiryBotService.submit(inquiry).getInquiryId());
    }
}
