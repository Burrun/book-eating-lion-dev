package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryCreateRequest(
        @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 2000) String content, boolean privateInquiry) {}
