package com.bookeatinglion.book.service;

import com.bookeatinglion.book.domain.*;
import com.bookeatinglion.book.dto.RecommendationCardResponse;
import com.bookeatinglion.book.dto.RecommendationQueueResponse;
import com.bookeatinglion.book.dto.RecommendationReactionRequest;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.exception.InvalidRecommendationReactionException;
import com.bookeatinglion.book.port.RecommendationAiPort;
import com.bookeatinglion.book.port.RecommendationAiPort.RankedBook;
import com.bookeatinglion.book.port.RecommendationQueuePort;
import com.bookeatinglion.book.repository.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private static final int CANDIDATE_LIMIT = 30;
    private static final int QUEUE_SIZE = 10;
    private static final Duration QUEUE_TTL = Duration.ofHours(6);

    private final BookRepository bookRepository;
    private final RecentViewedBookRepository recentRepository;
    private final WishlistRepository wishlistRepository;
    private final ReviewRepository reviewRepository;
    private final BookSwipeRepository swipeRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final RecommendationExposureRepository exposureRepository;
    private final ReviewPermissionRepository reviewPermissionRepository;
    private final RecommendationAiPort aiPort;
    private final RecommendationQueuePort queuePort;

    @Transactional
    public RecommendationQueueResponse getQueue(String memberId, boolean refresh) {
        if (!refresh) {
            Optional<RecommendationQueueResponse> cached = queuePort.get(memberId);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        Preference preference = preference(memberId);
        Set<Long> alreadySwiped = preference.swipes().stream()
                .map(swipe -> swipe.getBook().getBookId())
                .collect(Collectors.toSet());
        Set<Long> purchased = preference.permissions().stream()
                .map(ReviewPermission::getBookId)
                .collect(Collectors.toSet());
        List<Book> popularBooks = bookRepository
                .findBySaleStatusAndIsDeletedFalseOrderBySalesCountDescAverageRatingDesc(
                        SaleStatus.ON_SALE, PageRequest.of(0, CANDIDATE_LIMIT))
                .stream()
                .filter(book -> !alreadySwiped.contains(book.getBookId()))
                .filter(book -> !purchased.contains(book.getBookId()))
                .toList();

        String evidence = evidence(preference);
        List<RankedBook> semanticRanking =
                "행동 이력 없음".equals(evidence) ? List.of() : aiPort.rank(memberId, evidence, CANDIDATE_LIMIT);
        Map<Long, RankedBook> aiRanking = semanticRanking.stream()
                .collect(Collectors.toMap(RankedBook::bookId, Function.identity(), (left, right) -> left));

        Map<Long, Book> candidateMap = new LinkedHashMap<>();
        popularBooks.forEach(book -> candidateMap.put(book.getBookId(), book));
        bookRepository.findAllById(aiRanking.keySet()).stream()
                .filter(book -> !book.isDeleted() && book.getSaleStatus() == SaleStatus.ON_SALE)
                .filter(book -> !alreadySwiped.contains(book.getBookId()))
                .filter(book -> !purchased.contains(book.getBookId()))
                .forEach(book -> candidateMap.putIfAbsent(book.getBookId(), book));
        List<Book> books = new ArrayList<>(candidateMap.values());
        Map<Long, Double> ruleScores =
                books.stream().collect(Collectors.toMap(Book::getBookId, book -> ruleScore(book, preference)));

        List<ScoredBook> ranked = books.stream()
                .map(book -> {
                    RankedBook ai = aiRanking.get(book.getBookId());
                    double semantic = ai == null ? 0.0 : ai.semanticScore();
                    double finalScore = clamp(ruleScores.get(book.getBookId()) * 0.7 + semantic * 0.3);
                    String reason =
                            ai == null || ai.reason() == null || ai.reason().isBlank()
                                    ? fallbackReason(book, preference)
                                    : ai.reason();
                    return new ScoredBook(book, finalScore, reason);
                })
                .sorted(Comparator.comparingDouble(ScoredBook::score).reversed())
                .toList();

        List<ScoredBook> diverse = diversify(ranked);

        UUID queueId = UUID.randomUUID();
        List<RecommendationCardResponse> cards = new ArrayList<>();
        for (int i = 0; i < diverse.size(); i++) {
            ScoredBook item = diverse.get(i);
            cards.add(RecommendationCardResponse.of(item.book(), item.score(), item.reason()));
            exposureRepository.save(new RecommendationExposure(queueId, memberId, item.book(), i + 1));
        }

        RecommendationQueueResponse response = new RecommendationQueueResponse(queueId, cards);
        queuePort.put(memberId, response, QUEUE_TTL);
        return response;
    }

    @Transactional
    public void react(String memberId, RecommendationReactionRequest request) {
        exposureRepository
                .findByQueueIdAndMemberIdAndBook_BookId(request.queueId(), memberId, request.bookId())
                .orElseThrow(InvalidRecommendationReactionException::new);
        Book book = bookRepository
                .findByBookIdAndIsDeletedFalse(request.bookId())
                .orElseThrow(() -> new BookNotFoundException(request.bookId()));
        swipeRepository
                .findByMemberIdAndBook_BookId(memberId, request.bookId())
                .ifPresentOrElse(
                        swipe -> swipe.changeAction(request.action()),
                        () -> swipeRepository.save(new BookSwipe(memberId, book, request.action())));
        queuePort.removeCard(memberId, request.queueId(), request.bookId(), QUEUE_TTL);
    }

    private Preference preference(String memberId) {
        return new Preference(
                recentRepository.findByMemberId(memberId),
                wishlistRepository.findByMemberIdAndBook_IsDeletedFalseOrderByCreatedAtDesc(memberId),
                reviewRepository.findByMemberId(memberId),
                swipeRepository.findByMemberId(memberId),
                searchHistoryRepository.findByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, 20)),
                reviewPermissionRepository.findByIdMemberId(memberId));
    }

    private static double ruleScore(Book book, Preference preference) {
        double score = Math.min(0.20, book.getSalesCount() / 10_000.0)
                + Math.min(0.15, book.getAverageRating().doubleValue() / 5.0 * 0.15);
        for (RecentViewedBook viewed : preference.views()) {
            if (sameFeature(book, viewed.getBook())) {
                score += Math.min(0.15, viewed.getViewCount() * 0.02);
            }
        }
        for (Wishlist wishlist : preference.wishlists()) {
            if (sameFeature(book, wishlist.getBook())) {
                score += 0.10;
            }
        }
        for (Review review : preference.reviews()) {
            if (review.getRating() >= 4 && sameFeature(book, review.getBook())) {
                score += 0.12;
            }
        }
        for (BookSwipe swipe : preference.swipes()) {
            if (swipe.getAction() == SwipeAction.LIKE && sameFeature(book, swipe.getBook())) {
                score += 0.15;
            }
        }
        for (SearchHistory search : preference.searches()) {
            String query = search.getQueryText().toLowerCase(Locale.ROOT);
            if (book.getTitle().toLowerCase(Locale.ROOT).contains(query)
                    || book.getAuthor().toLowerCase(Locale.ROOT).contains(query)
                    || book.getCategory().toLowerCase(Locale.ROOT).contains(query)) {
                score += 0.08;
            }
        }
        return clamp(score);
    }

    private static boolean sameFeature(Book left, Book right) {
        return left.getCategory().equalsIgnoreCase(right.getCategory())
                || left.getAuthor().equalsIgnoreCase(right.getAuthor());
    }

    private static String evidence(Preference preference) {
        List<String> evidence = new ArrayList<>();
        preference.views().stream()
                .sorted(Comparator.comparingInt(RecentViewedBook::getViewCount).reversed())
                .limit(5)
                .forEach(view -> evidence.add("조회 %d회: %s / %s / %s"
                        .formatted(
                                view.getViewCount(),
                                view.getBook().getTitle(),
                                view.getBook().getAuthor(),
                                view.getBook().getCategory())));
        preference.wishlists().stream()
                .limit(5)
                .forEach(item -> evidence.add("찜: " + item.getBook().getTitle()));
        preference.reviews().stream()
                .filter(review -> review.getRating() >= 4)
                .limit(5)
                .forEach(review -> evidence.add("높은 별점 %d점: %s"
                        .formatted(review.getRating(), review.getBook().getTitle())));
        preference.swipes().stream()
                .filter(swipe -> swipe.getAction() == SwipeAction.LIKE)
                .limit(5)
                .forEach(swipe -> evidence.add("추천 카드 LIKE: " + swipe.getBook().getTitle()));
        preference.searches().stream().limit(5).forEach(search -> evidence.add("검색어: " + search.getQueryText()));
        preference.permissions().stream()
                .limit(5)
                .forEach(permission -> evidence.add("구매 확정 도서 ID: " + permission.getBookId()));
        return evidence.isEmpty() ? "행동 이력 없음" : String.join("\n", evidence);
    }

    private static String fallbackReason(Book book, Preference preference) {
        boolean related = preference.views().stream().anyMatch(view -> sameFeature(book, view.getBook()));
        return related ? "최근 살펴본 도서와 비슷한 분야라 추천했어요." : "평점과 판매량이 높은 도서라 새로운 취향 탐색을 위해 추천했어요.";
    }

    private static List<ScoredBook> diversify(List<ScoredBook> ranked) {
        Map<String, Integer> categoryCounts = new HashMap<>();
        List<ScoredBook> result = new ArrayList<>();
        for (ScoredBook item : ranked) {
            int count = categoryCounts.getOrDefault(item.book().getCategory(), 0);
            if (count >= 3) {
                continue;
            }
            result.add(item);
            categoryCounts.put(item.book().getCategory(), count + 1);
            if (result.size() == QUEUE_SIZE) {
                break;
            }
        }
        return result;
    }

    private static double clamp(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    private record Preference(
            List<RecentViewedBook> views,
            List<Wishlist> wishlists,
            List<Review> reviews,
            List<BookSwipe> swipes,
            List<SearchHistory> searches,
            List<ReviewPermission> permissions) {}

    private record ScoredBook(Book book, double score, String reason) {}
}
