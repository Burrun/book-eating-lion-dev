package com.bookeatinglion.member.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cognito")
public record CognitoProperties(
        String region,
        String userPoolId,
        String clientId,
        String clientSecret
) {
}
