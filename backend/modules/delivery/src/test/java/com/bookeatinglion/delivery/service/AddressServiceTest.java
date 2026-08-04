package com.bookeatinglion.delivery.service;

import com.bookeatinglion.delivery.domain.Address;
import com.bookeatinglion.delivery.dto.AddressCreateRequest;
import com.bookeatinglion.delivery.dto.AddressResponse;
import com.bookeatinglion.delivery.repository.AddressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    private Address address(Long id, String memberSub, boolean isDefault) {
        Address address = Address.builder()
                .memberSub(memberSub)
                .recipientName("홍길동")
                .phoneNumber("010-1234-5678")
                .zipcode("12345")
                .address("서울시 강남구")
                .detailAddress("101동 101호")
                .isDefault(isDefault)
                .build();
        ReflectionTestUtils.setField(address, "id", id);
        return address;
    }

    @Test
    void 배송지_목록을_조회한다() {
        when(addressRepository.findByMemberSubOrderByIsDefaultDescCreatedAtAsc("member-1"))
                .thenReturn(List.of(address(1L, "member-1", true), address(2L, "member-1", false)));

        List<AddressResponse> result = addressService.getAddressesByMemberSub("member-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    void 배송지를_등록한다() {
        AddressCreateRequest request = new AddressCreateRequest(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호", false);
        when(addressRepository.save(any(Address.class))).thenReturn(address(1L, "member-1", false));

        AddressResponse response = addressService.createAddress("member-1", request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.recipientName()).isEqualTo("홍길동");
        verify(addressRepository, never()).findByMemberSubAndIsDefaultTrue(any());
    }

    @Test
    void 기본_배송지로_등록하면_기존_기본배송지를_해제한다() {
        Address existingDefault = address(1L, "member-1", true);
        AddressCreateRequest request = new AddressCreateRequest(
                "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호", true);
        when(addressRepository.findByMemberSubAndIsDefaultTrue("member-1")).thenReturn(List.of(existingDefault));
        when(addressRepository.save(any(Address.class))).thenReturn(address(2L, "member-1", true));

        addressService.createAddress("member-1", request);

        assertThat(existingDefault.isDefault()).isFalse();

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }
}
