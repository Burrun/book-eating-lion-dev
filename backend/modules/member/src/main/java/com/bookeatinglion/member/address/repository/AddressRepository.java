package com.bookeatinglion.member.address.repository;

import com.bookeatinglion.member.address.domain.Address;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByMemberSubOrderByIsDefaultDescCreatedAtAsc(String memberSub);

    List<Address> findByMemberSubAndIsDefaultTrue(String memberSub);
}
