package com.library.admin.dto;

import lombok.Data;

@Data
public class UpdateStatusRequest {
    // true  = activate the student account
    // false = deactivate (block login)
    private boolean active;
}