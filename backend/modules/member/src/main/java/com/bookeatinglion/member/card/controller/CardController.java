package com.bookeatinglion.member.card.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.member.card.dto.CardResponse;
import com.bookeatinglion.member.card.dto.ChangeCardStatusRequest;
import com.bookeatinglion.member.card.dto.IssueCardRequest;
import com.bookeatinglion.member.card.service.CardService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<CardResponse> issueCard(@Valid @RequestBody(required = false) IssueCardRequest request) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(
                cardService.issueCard(memberId, request == null ? new IssueCardRequest(null) : request));
    }

    @GetMapping("/me")
    public ApiResponse<List<CardResponse>> getMyCards() {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(cardService.getMyCards(memberId));
    }

    @PatchMapping("/{cardId}/status")
    public ApiResponse<CardResponse> changeStatus(
            @PathVariable Long cardId, @Valid @RequestBody ChangeCardStatusRequest request) {
        Long memberId = SecurityUtils.currentMemberId();
        return ApiResponse.success(cardService.changeStatus(memberId, cardId, request.cardStatus()));
    }
}
