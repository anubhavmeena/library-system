package com.library.auth.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserInfoDto {
    private String id;
    private String name;
    private String mobile;
    private String email;
    private String role;
    private String photoUrl;
}