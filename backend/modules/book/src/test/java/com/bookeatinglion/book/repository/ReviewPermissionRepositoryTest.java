package com.bookeatinglion.book.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookeatinglion.book.BookModuleTestApplication;
import com.bookeatinglion.book.domain.ReviewPermission;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

@DataJpaTest
@ContextConfiguration(classes = BookModuleTestApplication.class)
class ReviewPermissionRepositoryTest {

    @Autowired
    private ReviewPermissionRepository reviewPermissionRepository;

    @BeforeEach
    void setUp() {
        reviewPermissionRepository.save(
                new ReviewPermission("member-1", 1L, 101L, "닉네임", LocalDateTime.now()));
    }

    @Test
    void 구매_확정한_회원_도서_조합은_존재한다() {
        boolean result = reviewPermissionRepository.existsByIdMemberIdAndBookId("member-1", 101L);

        assertThat(result).isTrue();
    }

    @Test
    void 다른_회원의_구매_확정_내역은_존재하지_않는다() {
        boolean result = reviewPermissionRepository.existsByIdMemberIdAndBookId("member-2", 101L);

        assertThat(result).isFalse();
    }

    @Test
    void 구매하지_않은_책은_존재하지_않는다() {
        boolean result = reviewPermissionRepository.existsByIdMemberIdAndBookId("member-1", 999L);

        assertThat(result).isFalse();
    }
}
