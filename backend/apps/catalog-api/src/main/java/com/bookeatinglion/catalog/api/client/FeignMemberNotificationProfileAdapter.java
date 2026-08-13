package com.bookeatinglion.catalog.api.client;

import com.bookeatinglion.book.port.MemberNotificationProfilePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeignMemberNotificationProfileAdapter implements MemberNotificationProfilePort {
    private final MemberNotificationClient client;

    @Override
    public NotificationProfile findByMemberId(String memberId) {
        var response = client.findByMemberId(memberId);
        return new NotificationProfile(response.memberId(), response.email(), response.name());
    }
}
