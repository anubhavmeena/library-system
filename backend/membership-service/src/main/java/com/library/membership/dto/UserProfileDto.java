package com.library.membership.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileDto {
    private String id;
    private String name;
    private String fatherName;
    private String mobile;
    private String email;
    private String photoUrl;
    private String dateOfBirth;
    private String gender;
}
