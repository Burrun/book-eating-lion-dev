package com.bookeatinglion.delivery.repository;

import com.bookeatinglion.delivery.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByMemberSubOrderByIsDefaultDescCreatedAtAsc(String memberSub);

    List<Address> findByMemberSubAndIsDefaultTrue(String memberSub);
}
