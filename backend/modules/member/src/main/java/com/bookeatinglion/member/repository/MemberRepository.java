package com.bookeatinglion.member.repository;

import com.bookeatinglion.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
