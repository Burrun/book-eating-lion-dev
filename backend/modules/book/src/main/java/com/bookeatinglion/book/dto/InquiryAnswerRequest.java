package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InquiryAnswerRequest(@NotBlank @Size(max = 4000) String answer) {}
