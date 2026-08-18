package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.SwipeAction;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecommendationReactionRequest(@NotNull UUID queueId, @NotNull Long bookId, @NotNull SwipeAction action) {}
