package com.bookeatinglion.book.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.book.domain.Book;
import com.bookeatinglion.book.domain.BookMemo;
import com.bookeatinglion.book.domain.SaleStatus;
import com.bookeatinglion.book.dto.BookMemoResponse;
import com.bookeatinglion.book.dto.FeedableMemoResponse;
import com.bookeatinglion.book.exception.BookNotFoundException;
import com.bookeatinglion.book.repository.BookMemoRepository;
import com.bookeatinglion.book.repository.BookRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookMemoServiceTest {

    private static final String MEMBER_SUB = "member-sub-1";

    @Mock
    private BookMemoRepository bookMemoRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookMemoService bookMemoService;

    private Book book(Long id) throws Exception {
        Book book = Book.builder()
                .title("책")
                .author("저자")
                .publisher("출판사")
                .isbn("978130000" + id)
                .category("소설")
                .price(10000)
                .saleStatus(SaleStatus.ON_SALE)
                .publishedDate(LocalDate.now())
                .salesCount(0)
                .build();
        Field idField = Book.class.getDeclaredField("bookId");
        idField.setAccessible(true);
        idField.set(book, id);
        return book;
    }

    @Test
    void 처음_쓰는_메모는_새로_저장한다() throws Exception {
        Book book = book(1L);
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(bookMemoRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L)).thenReturn(Optional.empty());
        when(bookMemoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BookMemoResponse response = bookMemoService.upsertMemo(1L, MEMBER_SUB, "이 책은 이런 내용이다");

        verify(bookMemoRepository, times(1)).save(any());
        assertThat(response.memoText()).isEqualTo("이 책은 이런 내용이다");
        assertThat(response.fedAt()).isNull();
    }

    @Test
    void 기존_메모가_있으면_새로_저장하지_않고_텍스트만_덮어쓴다() throws Exception {
        Book book = book(1L);
        BookMemo existing =
                BookMemo.builder().memberSub(MEMBER_SUB).book(book).memoText("옛 요약").build();
        when(bookRepository.findByBookIdAndIsDeletedFalse(1L)).thenReturn(Optional.of(book));
        when(bookMemoRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L)).thenReturn(Optional.of(existing));

        BookMemoResponse response = bookMemoService.upsertMemo(1L, MEMBER_SUB, "새 요약");

        verify(bookMemoRepository, never()).save(any());
        assertThat(response.memoText()).isEqualTo("새 요약");
    }

    @Test
    void 존재하지_않는_책에_메모_저장은_예외를_던진다() {
        when(bookRepository.findByBookIdAndIsDeletedFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookMemoService.upsertMemo(999L, MEMBER_SUB, "요약"))
                .isInstanceOf(BookNotFoundException.class);
    }

    @Test
    void 메모가_없으면_조회는_null을_반환한다() {
        when(bookRepository.existsByBookIdAndIsDeletedFalse(1L)).thenReturn(true);
        when(bookMemoRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L)).thenReturn(Optional.empty());

        assertThat(bookMemoService.getMemo(1L, MEMBER_SUB)).isNull();
    }

    @Test
    void 아직_안_먹인_메모만_피더블_목록에_나온다() throws Exception {
        Book book = book(1L);
        BookMemo unfed =
                BookMemo.builder().memberSub(MEMBER_SUB).book(book).memoText("요약").build();
        when(bookMemoRepository.findByMemberSubAndFedAtIsNullOrderByUpdatedAtDesc(MEMBER_SUB))
                .thenReturn(List.of(unfed));

        List<FeedableMemoResponse> result = bookMemoService.listFeedableMemos(MEMBER_SUB);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().bookId()).isEqualTo(1L);
    }

    @Test
    void 이미_먹인_메모만_먹인_목록에_나온다() throws Exception {
        Book book = book(1L);
        BookMemo fed = BookMemo.builder().memberSub(MEMBER_SUB).book(book).memoText("요약").build();
        fed.markFed(java.time.LocalDateTime.now());
        when(bookMemoRepository.findByMemberSubAndFedAtIsNotNullOrderByFedAtDesc(MEMBER_SUB))
                .thenReturn(List.of(fed));

        List<com.bookeatinglion.book.dto.FedMemoResponse> result = bookMemoService.listFedMemos(MEMBER_SUB);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().bookId()).isEqualTo(1L);
        assertThat(result.getFirst().memoText()).isEqualTo("요약");
    }

    @Test
    void 먹인_뒤에는_fedAt이_지금_시각으로_채워진다() throws Exception {
        Book book = book(1L);
        BookMemo memo = BookMemo.builder().memberSub(MEMBER_SUB).book(book).memoText("요약").build();
        when(bookMemoRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L)).thenReturn(Optional.of(memo));

        bookMemoService.markFed(1L, MEMBER_SUB);

        assertThat(memo.getFedAt()).isNotNull();
        assertThat(memo.isFed()).isTrue();
    }

    @Test
    void 메모_없이_먹이기_완료_처리하면_예외를_던진다() {
        when(bookMemoRepository.findByMemberSubAndBook_BookId(MEMBER_SUB, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookMemoService.markFed(1L, MEMBER_SUB)).isInstanceOf(IllegalStateException.class);
    }
}
