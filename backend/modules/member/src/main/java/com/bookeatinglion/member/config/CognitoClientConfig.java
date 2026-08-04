package com.bookeatinglion.member.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
public class CognitoClientConfig {

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(CognitoProperties properties) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
