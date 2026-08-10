package com.bookeatinglion.member.address.repository;

import com.bookeatinglion.member.address.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByMemberSubOrderByIsDefaultDescCreatedAtAsc(String memberSub);

    List<Address> findByMemberSubAndIsDefaultTrue(String memberSub);
}
