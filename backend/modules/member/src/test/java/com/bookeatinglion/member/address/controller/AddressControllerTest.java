package com.bookeatinglion.member.address.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.address.dto.AddressCreateRequest;
import com.bookeatinglion.member.address.dto.AddressResponse;
import com.bookeatinglion.member.address.dto.AddressUpdateRequest;
import com.bookeatinglion.member.address.exception.AddressNotFoundException;
import com.bookeatinglion.member.address.exception.UnauthorizedAddressAccessException;
import com.bookeatinglion.member.address.service.AddressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AddressController.class)
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class AddressControllerTest {

    private static final String SUB = "member-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AddressService addressService;

    private AddressResponse addressResponse() {
        return new AddressResponse(
                1L,
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울시 강남구",
                "101동 101호",
                true,
                LocalDateTime.now(),
                LocalDateTime.now());
    }

    @Test
    void 배송지_목록_조회는_200과_데이터를_반환한다() throws Exception {
        when(addressService.getAddressesByMemberSub(SUB)).thenReturn(List.of(addressResponse()));

        mockMvc.perform(get("/api/members/me/addresses").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recipientName").value("홍길동"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void 배송지_등록은_201과_데이터를_반환한다() throws Exception {
        AddressCreateRequest request =
                new AddressCreateRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호", true);
        when(addressService.createAddress(eq(SUB), any())).thenReturn(addressResponse());

        mockMvc.perform(post("/api/members/me/addresses")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.zipcode").value("12345"));
    }

    @Test
    void 필수값이_없으면_배송지_등록은_400을_반환한다() throws Exception {
        AddressCreateRequest invalid = new AddressCreateRequest("", "", "", "", null, false);

        mockMvc.perform(post("/api/members/me/addresses")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 배송지_수정은_200과_데이터를_반환한다() throws Exception {
        AddressUpdateRequest request = new AddressUpdateRequest("김철수", null, null, null, null, null);
        when(addressService.updateAddress(eq(SUB), eq(1L), any())).thenReturn(addressResponse());

        mockMvc.perform(patch("/api/members/me/addresses/1")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipientName").value("홍길동"));
    }

    @Test
    void 타인의_배송지_수정은_403을_반환한다() throws Exception {
        AddressUpdateRequest request = new AddressUpdateRequest("김철수", null, null, null, null, null);
        when(addressService.updateAddress(eq(SUB), eq(1L), any()))
                .thenThrow(new UnauthorizedAddressAccessException(1L));

        mockMvc.perform(patch("/api/members/me/addresses/1")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 존재하지_않는_배송지_수정은_404를_반환한다() throws Exception {
        AddressUpdateRequest request = new AddressUpdateRequest("김철수", null, null, null, null, null);
        when(addressService.updateAddress(eq(SUB), eq(999L), any())).thenThrow(new AddressNotFoundException(999L));

        mockMvc.perform(patch("/api/members/me/addresses/999")
                        .with(jwt().jwt(jwt -> jwt.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 배송지_삭제는_204를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/members/me/addresses/1").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isNoContent());

        verify(addressService).deleteAddress(SUB, 1L);
    }

    @Test
    void 타인의_배송지_삭제는_403을_반환한다() throws Exception {
        doThrow(new UnauthorizedAddressAccessException(1L)).when(addressService).deleteAddress(SUB, 1L);

        mockMvc.perform(delete("/api/members/me/addresses/1").with(jwt().jwt(jwt -> jwt.subject(SUB))))
                .andExpect(status().isForbidden());
    }
}
