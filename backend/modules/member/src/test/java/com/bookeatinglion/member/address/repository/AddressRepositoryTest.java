package com.bookeatinglion.member.address.repository;

import com.bookeatinglion.member.MemberModuleTestApplication;
import com.bookeatinglion.member.address.domain.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = MemberModuleTestApplication.class)
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @BeforeEach
    void setUp() {
        addressRepository.save(address("member-1", "집", true));
        addressRepository.save(address("member-1", "회사", false));
        addressRepository.save(address("member-2", "집", true));
    }

    private Address address(String memberSub, String recipientName, boolean isDefault) {
        return Address.builder()
                .memberSub(memberSub)
                .recipientName(recipientName)
                .phoneNumber("010-1234-5678")
                .zipcode("12345")
                .address("서울시 강남구")
                .detailAddress("101동 101호")
                .isDefault(isDefault)
                .build();
    }

    @Test
    void 회원의_배송지_목록을_기본배송지_우선으로_조회한다() {
        List<Address> result = addressRepository.findByMemberSubOrderByIsDefaultDescCreatedAtAsc("member-1");

        assertThat(result).extracting(Address::getRecipientName).containsExactly("집", "회사");
    }

    @Test
    void 회원의_기본_배송지만_조회한다() {
        List<Address> result = addressRepository.findByMemberSubAndIsDefaultTrue("member-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRecipientName()).isEqualTo("집");
    }
}
