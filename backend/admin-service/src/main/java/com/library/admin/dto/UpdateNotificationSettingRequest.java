package com.library.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateNotificationSettingRequest {

    @NotNull
    private Boolean sendToStudent;

    @NotNull
    private Boolean sendToAdmin;

    @NotNull
    private Boolean hindiEnabled;

    private String hindiTextStudent;
    private String hindiTextAdmin;
}
