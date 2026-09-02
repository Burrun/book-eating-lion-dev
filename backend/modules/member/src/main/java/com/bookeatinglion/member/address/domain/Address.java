package com.bookeatinglion.member.address.domain;

import com.bookeatinglion.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id")
    private Long id;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @Column(nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String phoneNumber;

    @Column(name = "postal_code", nullable = false)
    private String zipcode;

    @Column(nullable = false)
    private String address;

    @Column(name = "address_detail")
    private String detailAddress;

    @Column(nullable = false)
    private boolean isDefault;

    @Builder
    public Address(
            String memberSub,
            String recipientName,
            String phoneNumber,
            String zipcode,
            String address,
            String detailAddress,
            boolean isDefault) {
        this.memberSub = memberSub;
        this.recipientName = recipientName;
        this.phoneNumber = phoneNumber;
        this.zipcode = zipcode;
        this.address = address;
        this.detailAddress = detailAddress;
        this.isDefault = isDefault;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }

    public void markAsDefault() {
        this.isDefault = true;
    }

    /** null 인 필드는 기존 값을 유지한다(Member.updateProfile 과 동일한 부분수정 관례). */
    public void update(String recipientName, String phoneNumber, String zipcode, String address, String detailAddress) {
        if (recipientName != null) {
            this.recipientName = recipientName;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (zipcode != null) {
            this.zipcode = zipcode;
        }
        if (address != null) {
            this.address = address;
        }
        if (detailAddress != null) {
            this.detailAddress = detailAddress;
        }
    }

    public boolean isOwnedBy(String memberSub) {
        return this.memberSub.equals(memberSub);
    }
}
