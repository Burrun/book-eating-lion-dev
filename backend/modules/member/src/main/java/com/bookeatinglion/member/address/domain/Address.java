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
    private Long id;

    @Column(name = "member_sub", nullable = false)
    private String memberSub;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String zipcode;

    @Column(nullable = false)
    private String address;

    private String detailAddress;

    @Column(nullable = false)
    private boolean isDefault;

    @Builder
    public Address(String memberSub, String recipientName, String phoneNumber, String zipcode,
                    String address, String detailAddress, boolean isDefault) {
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
}
