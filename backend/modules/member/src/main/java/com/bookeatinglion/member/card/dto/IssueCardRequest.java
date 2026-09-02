package com.bookeatinglion.member.card.dto;

import jakarta.validation.constraints.Min;

public record IssueCardRequest(@Min(1000) Long monthlyLimit) {}
