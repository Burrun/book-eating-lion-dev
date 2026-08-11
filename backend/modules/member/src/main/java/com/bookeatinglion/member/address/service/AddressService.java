package com.bookeatinglion.member.address.service;

import com.bookeatinglion.member.address.domain.Address;
import com.bookeatinglion.member.address.dto.AddressCreateRequest;
import com.bookeatinglion.member.address.dto.AddressResponse;
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
}
