package com.library.admin.dto;

import lombok.Data;

@Data
public class UpdateStudentRequest {
    private String name;
    private String mobile;
    private String email;
    private String address;
    private String gender;
    private String dateOfBirth;
}
