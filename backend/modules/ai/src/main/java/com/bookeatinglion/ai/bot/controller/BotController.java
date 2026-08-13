package com.bookeatinglion.ai.bot.controller;

import com.bookeatinglion.ai.bot.service.InquiryBotService;
import com.bookeatinglion.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/ai/bot/** → Ingress 가 ai-bot Deployment 로 라우팅한다.
 *
 * <p>1:1 문의 등록({@code POST /inquiries})은 없앴다. 실시간 상담 채널이 그 자리를
 * 대신하고, 상담사가 없으면 문의 게시판으로 안내한다.
 */
@RestController
@RequestMapping("/api/ai/bot")
@RequiredArgsConstructor
public class BotController {

    private final InquiryBotService inquiryBotService;

    public record AskRequest(@NotBlank String question) {}

    @PostMapping("/ask")
    public ApiResponse<String> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.success(inquiryBotService.answer(request.question()));
    }
}
