package com.library.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 2, max = 100) private String name;
    @Size(max = 100)          private String fatherName;
    @Size(max = 500)          private String address;
    private String dateOfBirth;  // yyyy-MM-dd
    private String gender;       // Male, Female, Other
    @Size(max = 255)          private String email;
}