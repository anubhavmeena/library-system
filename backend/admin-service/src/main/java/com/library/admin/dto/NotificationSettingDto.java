package com.library.admin.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationSettingDto {
    private String  notificationKey;
    private boolean sendToStudent;
    private boolean sendToAdmin;
    private boolean studentEditable; // false = "Send to Student" is fixed, frontend must disable the checkbox
    private boolean adminEditable;   // false = "Send to Admin" is fixed, frontend must disable the checkbox
    private boolean hindiEditable;   // false = this notification has no Hindi translation option at all
    private boolean hindiEnabled;
    private String  hindiTextStudent;
    private String  hindiTextAdmin;
    private String  updatedAt;
}
