package com.example.demo.domain.member;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * 서비스 회원 객체
 */
@Data
public class Member {
    private Long id;

    @NotEmpty
    private String loginId;
    @NotEmpty
    private String name;
    @NotEmpty
    private String password;
}
