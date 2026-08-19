package com.bookeatinglion.member.subscription.dto;

import com.bookeatinglion.member.subscription.domain.PlanType;
import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(@NotNull PlanType planType) {}
