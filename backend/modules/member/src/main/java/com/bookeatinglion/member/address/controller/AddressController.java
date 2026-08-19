package com.bookeatinglion.member.address.controller;

import com.bookeatinglion.common.dto.ApiResponse;
import com.bookeatinglion.common.security.SecurityUtils;
import com.bookeatinglion.member.address.dto.AddressCreateRequest;
import com.bookeatinglion.member.address.dto.AddressResponse;
import com.bookeatinglion.member.address.dto.AddressUpdateRequest;
import com.bookeatinglion.member.address.service.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> getMyAddresses() {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(addressService.getAddressesByMemberSub(memberSub));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<AddressResponse> createAddress(@Valid @RequestBody AddressCreateRequest request) {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(addressService.createAddress(memberSub, request));
    }

    @PatchMapping("/{addressId}")
    public ApiResponse<AddressResponse> updateAddress(
            @PathVariable Long addressId, @RequestBody AddressUpdateRequest request) {
        String memberSub = SecurityUtils.currentMemberSub();
        return ApiResponse.success(addressService.updateAddress(memberSub, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long addressId) {
        String memberSub = SecurityUtils.currentMemberSub();
        addressService.deleteAddress(memberSub, addressId);
        return ResponseEntity.noContent().build();
    }
}
