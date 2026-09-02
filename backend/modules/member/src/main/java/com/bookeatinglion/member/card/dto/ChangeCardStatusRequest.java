package com.bookeatinglion.member.card.dto;

import com.bookeatinglion.member.card.domain.CardStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeCardStatusRequest(@NotNull CardStatus cardStatus) {}
