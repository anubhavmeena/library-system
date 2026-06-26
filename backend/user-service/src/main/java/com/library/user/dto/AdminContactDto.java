package com.library.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminContactDto {
    private String name;
    private String mobile;
    private String email;
}
