package com.bookeatinglion.delivery.service;

import com.bookeatinglion.delivery.domain.Address;
import com.bookeatinglion.delivery.dto.AddressCreateRequest;
import com.bookeatinglion.delivery.dto.AddressResponse;
import com.bookeatinglion.delivery.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
