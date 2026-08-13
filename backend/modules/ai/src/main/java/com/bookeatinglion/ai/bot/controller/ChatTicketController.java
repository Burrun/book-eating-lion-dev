package com.bookeatinglion.ai.bot.controller;

import com.bookeatinglion.ai.bot.chat.ChatIdentity;
import com.bookeatinglion.ai.bot.chat.ChatTicketStore;
import com.bookeatinglion.ai.bot.config.ChatProperties;
import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 접속용 교환권 발급. 평범한 REST 라 Security 필터 체인 위에서 신원이 확정된다.
 *
 * <p>
 * 🔴 <b>상담사 판정을 여기서 끝내고 티켓에 굳혀 넣는다.</b> WebSocket 쪽에서 다시
 * 판정하지 않는다 — 판정 지점이 둘이면 언젠가 두 곳의 규칙이 갈라진다.
 */
@RestController
@RequestMapping("/api/ai/bot/chat")
@RequiredArgsConstructor
public class ChatTicketController {

    private final ChatTicketStore tickets;
    private final ChatProperties props;

    public record TicketResponse(String ticket, long expiresInSeconds) {}

    @PostMapping("/ticket")
    public ApiResponse<TicketResponse> issue(@AuthenticationPrincipal Jwt jwt) {
        String ticket = tickets.issue(
                new ChatIdentity(SecurityUtils.currentMemberSub(), SecurityUtils.currentNickname(), isAgent(jwt)));

        return ApiResponse.success(new TicketResponse(ticket, props.ticketTtl().toSeconds()));
    }

    /**
     * {@code cognito:groups}만을 본다. 상담사 권한은 Cognito 그룹에 수동으로 넣어 부여한다 —
     * 애플리케이션에 상담사를 임명하는 코드는 없고, 있어서도 안 된다.
     */
    private static boolean isAgent(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        return groups != null && groups.contains("ADMIN");
    }
}
