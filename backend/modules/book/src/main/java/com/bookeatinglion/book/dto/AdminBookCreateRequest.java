package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.SaleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AdminBookCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100) String author,
        @NotBlank @Size(max = 100) String publisher,
        @NotBlank @Pattern(regexp = "\\d{13}") String isbn,
        @NotBlank @Size(max = 100) String category,
        @Min(0) int price,
        @Size(max = 500) String coverImageUrl,
        String description,
        String detailedSynopsis,
        SaleStatus saleStatus,
        LocalDate publishedDate
) {}
