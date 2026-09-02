package com.bookeatinglion.catalog.api.client;

import com.bookeatinglion.book.port.MemberSubscriptionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeignMemberSubscriptionAdapter implements MemberSubscriptionPort {

    private final MemberSubscriptionClient client;

    @Override
    public boolean isSubscribed(String memberId) {
        return client.getSubscriptionStatus(memberId).subscribed();
    }
}
