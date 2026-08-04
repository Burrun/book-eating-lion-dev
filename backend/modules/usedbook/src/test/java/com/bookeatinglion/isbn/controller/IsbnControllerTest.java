package com.bookeatinglion.isbn.controller;

import com.bookeatinglion.isbn.dto.IsbnLookupResponse;
import com.bookeatinglion.isbn.service.IsbnLookupService;
import com.bookeatinglion.usedbook.UsedBookModuleTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IsbnController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = UsedBookModuleTestApplication.class)
class IsbnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IsbnLookupService isbnLookupService;

    @Test
    void ISBN_조회는_200과_데이터를_반환한다() throws Exception {
        when(isbnLookupService.lookup("9791100000001"))
                .thenReturn(new IsbnLookupResponse("9791100000001", "테스트책", "저자", "출판사", "cover.jpg", "설명"));

        mockMvc.perform(get("/api/isbn/9791100000001/lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("테스트책"))
                .andExpect(jsonPath("$.data.isbn").value("9791100000001"));
    }
}
