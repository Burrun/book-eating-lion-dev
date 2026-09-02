package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * selectedText 의 길이 상한은 여기 두지 않는다 — 운영 중에 조절할 값이라 설정
 * (catalog.highlight.max-selected-chars)으로 빼고 BookHighlightService 가 검증한다.
 * 애노테이션의 max 는 컴파일 상수여야 해서 설정값을 넣을 수 없다.
 */
public record BookHighlightRequest(
        @NotBlank @Size(max = 500) String cfiRange, @NotBlank String selectedText, @Size(max = 4000) String memoText) {}
