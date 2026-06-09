package com.library.membership.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserApiResponse {
    private boolean success;
    private UserProfileDto data;
}
