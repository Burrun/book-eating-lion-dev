package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Category;
import com.bookeatinglion.book.dto.CategoryCreateRequest;
import com.bookeatinglion.book.dto.CategoryResponse;
import com.bookeatinglion.book.exception.CatalogConflictException;
import com.bookeatinglion.book.repository.BookRepository;
import com.bookeatinglion.book.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void 사용자에게는_활성_카테고리만_정렬해서_반환한다() {
        when(categoryRepository.findByActiveTrueOrderBySortOrderAscCategoryNameAsc())
                .thenReturn(List.of(new Category("소설", null, 1)));

        List<CategoryResponse> result = categoryService.getActiveCategories();

        assertThat(result).extracting(CategoryResponse::categoryName).containsExactly("소설");
    }

    @Test
    void 중복되지_않은_카테고리를_등록한다() {
        CategoryCreateRequest request = new CategoryCreateRequest("인문", null, 2);
        when(categoryRepository.existsByCategoryName("인문")).thenReturn(false);
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse result = categoryService.create(request);

        assertThat(result.categoryName()).isEqualTo("인문");
    }

    @Test
    void 활성_도서가_있는_카테고리는_비활성화하지_않는다() {
        Category category = new Category("소설", null, 1);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(bookRepository.existsByCategoryAndIsDeletedFalse("소설")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deactivate(1L))
                .isInstanceOf(CatalogConflictException.class);
        assertThat(category.isActive()).isTrue();
    }

    @Test
    void 도서가_없는_카테고리는_비활성화한다() {
        Category category = new Category("소설", null, 1);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.deactivate(1L);

        assertThat(category.isActive()).isFalse();
        verify(bookRepository).existsByCategoryAndIsDeletedFalse("소설");
    }
}
