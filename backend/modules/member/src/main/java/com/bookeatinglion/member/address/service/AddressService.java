package com.bookeatinglion.member.address.service;

import com.bookeatinglion.member.address.domain.Address;
import com.bookeatinglion.member.address.dto.AddressCreateRequest;
import com.bookeatinglion.member.address.dto.AddressResponse;
import com.bookeatinglion.member.address.dto.AddressUpdateRequest;
import com.bookeatinglion.member.address.exception.AddressNotFoundException;
import com.bookeatinglion.member.address.exception.UnauthorizedAddressAccessException;
import com.bookeatinglion.member.address.repository.AddressRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;

    public List<AddressResponse> getAddressesByMemberSub(String memberSub) {
        return addressRepository.findByMemberSubOrderByIsDefaultDescCreatedAtAsc(memberSub).stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(String memberSub, AddressCreateRequest request) {
        if (request.isDefault()) {
            addressRepository.findByMemberSubAndIsDefaultTrue(memberSub).forEach(Address::unsetDefault);
        }

        Address address = Address.builder()
                .memberSub(memberSub)
                .recipientName(request.recipientName())
                .phoneNumber(request.phoneNumber())
                .zipcode(request.zipcode())
                .address(request.address())
                .detailAddress(request.detailAddress())
                .isDefault(request.isDefault())
                .build();

        return AddressResponse.from(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(String memberSub, Long addressId, AddressUpdateRequest request) {
        Address address = getOwnedAddress(memberSub, addressId);

        if (Boolean.TRUE.equals(request.isDefault())) {
            addressRepository.findByMemberSubAndIsDefaultTrue(memberSub).forEach(Address::unsetDefault);
            address.markAsDefault();
        } else if (Boolean.FALSE.equals(request.isDefault())) {
            address.unsetDefault();
        }

        address.update(
                request.recipientName(),
                request.phoneNumber(),
                request.zipcode(),
                request.address(),
                request.detailAddress());

        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(String memberSub, Long addressId) {
        addressRepository.delete(getOwnedAddress(memberSub, addressId));
    }

    private Address getOwnedAddress(String memberSub, Long addressId) {
        Address address =
                addressRepository.findById(addressId).orElseThrow(() -> new AddressNotFoundException(addressId));
        if (!address.isOwnedBy(memberSub)) {
            throw new UnauthorizedAddressAccessException(addressId);
        }
        return address;
    }
}
