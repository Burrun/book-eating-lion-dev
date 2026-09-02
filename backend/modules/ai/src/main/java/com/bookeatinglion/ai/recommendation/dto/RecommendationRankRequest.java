package com.bookeatinglion.ai.recommendation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RecommendationRankRequest(
        @NotBlank String memberId, @NotBlank String preferenceEvidence, @Min(1) @Max(50) int topK) {}
