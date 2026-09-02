import { apiClient, unwrap } from "./client.ts";
import { mockDelay } from "../mocks/delay.ts";
import { mockGetRecommendationQueue, mockReactRecommendation } from "../mocks/recommendations.ts";
import type {
  ApiResponse,
  RecommendationQueueResponse,
  RecommendationReactionRequest,
} from "./types.ts";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export async function getRecommendationQueue(
  refresh = false,
): Promise<RecommendationQueueResponse> {
  if (USE_MOCK) return mockDelay(mockGetRecommendationQueue(refresh));
  return unwrap(
    apiClient.get<ApiResponse<RecommendationQueueResponse>>("/catalog/recommend/queue", {
      params: { refresh },
    }),
  );
}

export async function reactRecommendation(request: RecommendationReactionRequest): Promise<void> {
  if (USE_MOCK) {
    mockReactRecommendation(request);
    return mockDelay(undefined, 150);
  }
  await unwrap(apiClient.post<ApiResponse<void>>("/catalog/recommend/queue/reaction", request));
}
