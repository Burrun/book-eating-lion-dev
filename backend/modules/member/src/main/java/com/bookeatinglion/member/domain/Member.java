package com.bookeatinglion.member.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String cognitoSub;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Builder
    public Member(String cognitoSub, String email, String name) {
        this.cognitoSub = cognitoSub;
        this.email = email;
        this.name = name;
        this.role = Role.USER;
    }

    public static Member register(String cognitoSub, String email, String name) {
        return Member.builder()
                .cognitoSub(cognitoSub)
                .email(email)
                .name(name)
                .build();
    }

    /**
     * 프로필 정보를 부분 수정한다. 각 파라미터가 null이면 해당 필드는 기존 값을 유지하며,
     * null을 전달하여 값을 삭제(초기화)하는 기능은 지원하지 않는다.
     */
    public void updateProfile(String name, String phoneNumber, Gender gender, LocalDate birthDate) {
        if (name != null) {
            this.name = name;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
    }
}
