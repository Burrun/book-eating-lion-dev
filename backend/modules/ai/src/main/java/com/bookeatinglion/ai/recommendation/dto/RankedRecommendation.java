package com.bookeatinglion.ai.recommendation.dto;

public record RankedRecommendation(Long bookId, double semanticScore, String reason) {}
