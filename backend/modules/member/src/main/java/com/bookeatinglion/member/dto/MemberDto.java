package com.bookeatinglion.member.dto;

import com.bookeatinglion.member.domain.Gender;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원(Member) 도메인에서 사용하는 요청/응답 DTO 모음.
 *
 * <p>{@code AuthDto}와 마찬가지로 베타 프로젝트의 컨벤션에 따라
 * 하나의 클래스 안에 {@code static nested class}로 관련 DTO를 묶어 관리한다.</p>
 */
public class MemberDto {

    private MemberDto() {
        // 네임스페이스 역할만 하는 클래스이므로 인스턴스화하지 않는다.
    }

    /** {@code GET /api/members/me} 응답 바디. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberResponse {
        private Long memberId;
        private String username;
        private String name;
        private Gender gender;
        private Integer age;
        private String role;
        private String grade;
        private long point;
        private LocalDateTime createdAt;
    }

    /**
     * {@code PATCH /api/members/me} 요청 바디.
     *
     * <p>PATCH(부분 수정) 의미를 살리기 위해 모든 필드를 선택 입력으로 두며,
     * {@code null}인 필드는 기존 값을 그대로 유지한다.</p>
     */
    @Getter
    @Setter
    public static class MemberUpdateRequest {
        private String name;
        private Gender gender;

        @Min(value = 1, message = "나이는 1살 이상이어야 합니다.")
        private Integer age;
    }

    /** {@code GET /api/members/me/grade} 응답 바디. */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class MemberGradeResponse {
        private String grade;
        private long point;
    }
}
