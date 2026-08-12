package com.bookeatinglion.order.cart.controller;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.order.OrderModuleTestApplication;
import com.bookeatinglion.order.cart.dto.CartItemView;
import com.bookeatinglion.order.cart.dto.CartResponse;
import com.bookeatinglion.order.cart.exception.CartItemNotFoundException;
import com.bookeatinglion.order.cart.exception.UnauthorizedCartAccessException;
import com.bookeatinglion.order.cart.service.CartService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = {CartController.class, CartExceptionHandler.class})
@ContextConfiguration(classes = OrderModuleTestApplication.class)
class CartControllerTest {

    private static final String MEMBER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    private static RequestPostProcessor authenticated() {
        return jwt().jwt(jwt -> jwt.subject(MEMBER_ID));
    }

    private CartItemView cartItemView() {
        return new CartItemView(1L, 100L, "책1", 10000, "http://img/100", 2, 20000L);
    }

    @Test
    void 장바구니_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(cartService.getCart(MEMBER_ID)).thenReturn(new CartResponse(List.of(cartItemView()), 2, 20000L));

        mockMvc.perform(get("/api/cart").with(authenticated()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.items[0].bookId").value(100));
    }

    @Test
    void 장바구니_담기는_200과_생성된_항목을_반환한다() throws Exception {
        when(cartService.addItem(eq(MEMBER_ID), eq(100L), eq(2))).thenReturn(cartItemView());

        mockMvc.perform(post("/api/cart")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":100,\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(2));
    }

    @Test
    void quantity를_생략하면_기본값_1이_적용된다() throws Exception {
        when(cartService.addItem(eq(MEMBER_ID), eq(100L), eq(1)))
                .thenReturn(new CartItemView(1L, 100L, "책1", 10000, "http://img/100", 1, 10000L));

        mockMvc.perform(post("/api/cart")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quantity").value(1));
    }

    @Test
    void 수량이_0이하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/cart")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":100,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 수량_변경은_200과_변경된_항목을_반환한다() throws Exception {
        when(cartService.changeQuantity(eq(MEMBER_ID), eq(1L), eq(5)))
                .thenReturn(new CartItemView(1L, 100L, "책1", 10000, "http://img/100", 5, 50000L));

        mockMvc.perform(patch("/api/cart/1")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5));
    }

    @Test
    void 타인의_항목_수량변경은_403을_반환한다() throws Exception {
        when(cartService.changeQuantity(eq(MEMBER_ID), eq(1L), anyInt()))
                .thenThrow(new UnauthorizedCartAccessException(1L));

        mockMvc.perform(patch("/api/cart/1")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_항목_수량변경은_404를_반환한다() throws Exception {
        when(cartService.changeQuantity(eq(MEMBER_ID), anyLong(), anyInt()))
                .thenThrow(new CartItemNotFoundException(999L));

        mockMvc.perform(patch("/api/cart/999")
                        .with(authenticated())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 항목_삭제는_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/cart/1").with(authenticated()).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
