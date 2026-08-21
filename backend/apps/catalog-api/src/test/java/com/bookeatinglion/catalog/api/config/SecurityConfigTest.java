package com.bookeatinglion.catalog.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.book.controller.BookExceptionHandler;
import com.bookeatinglion.book.controller.EbookController;
import com.bookeatinglion.book.controller.ReadingProgressController;
import com.bookeatinglion.book.security.CatalogMemberIdentity;
import com.bookeatinglion.book.service.EbookService;
import com.bookeatinglion.book.service.ReadingProgressService;
import com.bookeatinglion.catalog.api.test.CatalogApiModuleTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SecurityConfig의 실제 authorizeHttpRequests 규칙(순서 포함)을 검증한다.
 *
 * 회귀 배경: "/api/catalog/books/*_/reading-progress"를 authenticated()로 추가했을 때, 먼저 선언된
 * "GET /api/catalog/books/**".permitAll() 규칙이 먼저 매칭돼 GET이 인증 없이 통과하는 버그가 있었다
 * (첫 매칭 규칙이 이기는 authorizeHttpRequests의 특성). 규칙 순서를 permitAll보다 앞으로
 * 옮겨서 고쳤는데, book 모듈의 ReadingProgressControllerTest는 이 SecurityConfig 빈을 아예
 * 로드하지 않아 이 순서 문제를 검증하지 못한다 — jwt() 로 인증된 요청만 확인했을 뿐,
 * "인증 없이" 케이스는 Spring Security의 일반 기본값(전부 인증 필요)에 기대고 있었다.
 * 그 기본값은 permitAll 규칙이 있든 없든 항상 인증을 요구하므로, 이 순서 버그를 절대
 * 잡아내지 못한다. 이 테스트는 실제 SecurityConfig 빈을 @Import 해서 그 특정 규칙 순서를
 * 직접 검증한다.
 */
@WebMvcTest(controllers = {ReadingProgressController.class, EbookController.class, BookExceptionHandler.class})
@Import({SecurityConfig.class, CatalogMemberIdentity.class})
@ContextConfiguration(classes = CatalogApiModuleTestApplication.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReadingProgressService readingProgressService;

    @MockBean
    private EbookService ebookService;

    @Test
    void 인증_없이_이어읽기_위치_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/books/1/reading-progress")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_이어읽기_위치_저장은_401을_반환한다() throws Exception {
        mockMvc.perform(put("/api/catalog/books/1/reading-progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cfi\":\"epubcfi(/6/4)\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_ebook_URL_발급은_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/books/1/ebook")).andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_없이_내_리뷰_목록_조회는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/catalog/reviews/me")).andExpect(status().isUnauthorized());
    }
}
