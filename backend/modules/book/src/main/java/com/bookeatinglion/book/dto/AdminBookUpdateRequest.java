package com.bookeatinglion.book.dto;

import com.bookeatinglion.book.domain.SaleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AdminBookUpdateRequest(
        @Size(min = 1, max = 200) String title,
        @Size(min = 1, max = 100) String author,
        @Size(min = 1, max = 100) String publisher,
        @Pattern(regexp = "\\d{13}") String isbn,
        @Size(min = 1, max = 100) String category,
        @Min(0) Integer price,
        @Size(max = 500) String coverImageUrl,
        String description,
        String detailedSynopsis,
        SaleStatus saleStatus,
        LocalDate publishedDate
) {}
