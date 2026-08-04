package com.bookeatinglion.delivery.controller;

import com.bookeatinglion.delivery.DeliveryModuleTestApplication;
import com.bookeatinglion.delivery.dto.AddressCreateRequest;
import com.bookeatinglion.delivery.dto.AddressResponse;
import com.bookeatinglion.delivery.service.AddressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressController.class)
@ContextConfiguration(classes = DeliveryModuleTestApplication.class)
class AddressControllerTest {

    private static final String SUB = "member-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AddressService addressService;

    private AddressResponse addressResponse() {
        return new AddressResponse(1L, "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호",
                true, LocalDateTime.now(), LocalDateTime.now());
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
        AddressCreateRequest request = new AddressCreateRequest(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호", true);
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
}
