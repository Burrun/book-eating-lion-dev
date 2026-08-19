package com.bookeatinglion.member.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    // Cognito가 발급한 sub 값을 그대로 PK로 쓴다(팀 컨벤션 확정). auto-increment가 아니라
    // @GeneratedValue를 붙이지 않는다 — register()에서 Cognito sub를 그대로 대입한다.
    @Id
    @Column(name = "member_id")
    private String id;

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
    public Member(String id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = Role.USER;
    }

    /** cognitoSub: Cognito가 발급한 sub. 이 값이 그대로 PK(member_id)가 된다. */
    public static Member register(String cognitoSub, String email, String name) {
        return Member.builder().id(cognitoSub).email(email).name(name).build();
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
