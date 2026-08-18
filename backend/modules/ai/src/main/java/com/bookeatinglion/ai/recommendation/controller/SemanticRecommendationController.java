package com.bookeatinglion.ai.recommendation.controller;

import com.bookeatinglion.ai.recommendation.dto.RankedRecommendation;
import com.bookeatinglion.ai.recommendation.dto.RecommendationRankRequest;
import com.bookeatinglion.ai.recommendation.service.RecommendationRagService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ai/recommendations")
@RequiredArgsConstructor
public class SemanticRecommendationController {

    private final RecommendationRagService service;

    @PostMapping("/rank")
    public List<RankedRecommendation> rank(@Valid @RequestBody RecommendationRankRequest request) {
        return service.rank(request);
    }
}
