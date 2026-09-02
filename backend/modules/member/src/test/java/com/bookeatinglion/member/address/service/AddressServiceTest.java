package com.bookeatinglion.member.address.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookeatinglion.member.address.domain.Address;
import com.bookeatinglion.member.address.dto.AddressCreateRequest;
import com.bookeatinglion.member.address.dto.AddressResponse;
import com.bookeatinglion.member.address.dto.AddressUpdateRequest;
import com.bookeatinglion.member.address.exception.AddressNotFoundException;
import com.bookeatinglion.member.address.exception.UnauthorizedAddressAccessException;
import com.bookeatinglion.member.address.repository.AddressRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
        AddressCreateRequest request =
                new AddressCreateRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호", false);
        when(addressRepository.save(any(Address.class))).thenReturn(address(1L, "member-1", false));

        AddressResponse response = addressService.createAddress("member-1", request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.recipientName()).isEqualTo("홍길동");
        verify(addressRepository, never()).findByMemberSubAndIsDefaultTrue(any());
    }

    @Test
    void 기본_배송지로_등록하면_기존_기본배송지를_해제한다() {
        Address existingDefault = address(1L, "member-1", true);
        AddressCreateRequest request =
                new AddressCreateRequest("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101동 101호", true);
        when(addressRepository.findByMemberSubAndIsDefaultTrue("member-1")).thenReturn(List.of(existingDefault));
        when(addressRepository.save(any(Address.class))).thenReturn(address(2L, "member-1", true));

        addressService.createAddress("member-1", request);

        assertThat(existingDefault.isDefault()).isFalse();

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void 본인_배송지를_수정한다() {
        Address address = address(1L, "member-1", false);
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        AddressUpdateRequest request = new AddressUpdateRequest("김철수", null, null, null, null, null);

        AddressResponse response = addressService.updateAddress("member-1", 1L, request);

        assertThat(response.recipientName()).isEqualTo("김철수");
        assertThat(response.phoneNumber()).isEqualTo("010-1234-5678"); // null 전달 필드는 유지
    }

    @Test
    void 기본배송지로_수정하면_기존_기본배송지를_해제한다() {
        Address existingDefault = address(1L, "member-1", true);
        Address target = address(2L, "member-1", false);
        when(addressRepository.findById(2L)).thenReturn(Optional.of(target));
        when(addressRepository.findByMemberSubAndIsDefaultTrue("member-1")).thenReturn(List.of(existingDefault));
        AddressUpdateRequest request = new AddressUpdateRequest(null, null, null, null, null, true);

        AddressResponse response = addressService.updateAddress("member-1", 2L, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(existingDefault.isDefault()).isFalse();
    }

    @Test
    void 기본배송지_해제로_수정할_수_있다() {
        Address address = address(1L, "member-1", true);
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        AddressUpdateRequest request = new AddressUpdateRequest(null, null, null, null, null, false);

        AddressResponse response = addressService.updateAddress("member-1", 1L, request);

        assertThat(response.isDefault()).isFalse();
    }

    @Test
    void 타인의_배송지_수정은_예외를_던진다() {
        Address address = address(1L, "member-2", false);
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));
        AddressUpdateRequest request = new AddressUpdateRequest("김철수", null, null, null, null, null);

        assertThatThrownBy(() -> addressService.updateAddress("member-1", 1L, request))
                .isInstanceOf(UnauthorizedAddressAccessException.class);
    }

    @Test
    void 존재하지_않는_배송지_수정은_예외를_던진다() {
        when(addressRepository.findById(999L)).thenReturn(Optional.empty());
        AddressUpdateRequest request = new AddressUpdateRequest("김철수", null, null, null, null, null);

        assertThatThrownBy(() -> addressService.updateAddress("member-1", 999L, request))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void 본인_배송지를_삭제한다() {
        Address address = address(1L, "member-1", false);
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

        addressService.deleteAddress("member-1", 1L);

        verify(addressRepository).delete(address);
    }

    @Test
    void 타인의_배송지_삭제는_예외를_던진다() {
        Address address = address(1L, "member-2", false);
        when(addressRepository.findById(1L)).thenReturn(Optional.of(address));

        assertThatThrownBy(() -> addressService.deleteAddress("member-1", 1L))
                .isInstanceOf(UnauthorizedAddressAccessException.class);
    }
}
