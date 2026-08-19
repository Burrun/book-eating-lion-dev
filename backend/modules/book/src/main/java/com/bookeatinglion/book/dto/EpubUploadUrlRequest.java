package com.bookeatinglion.book.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EpubUploadUrlRequest(@NotBlank @Size(max = 255) String fileName) {}
