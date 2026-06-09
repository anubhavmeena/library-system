package com.library.admin.dto;

import lombok.Data;
import java.util.List;

@Data
public class SendReminderRequest {
    // List of student user UUIDs to send reminders to.
    // If empty or null → sends to ALL students with memberships
    // expiring within the next 7 days.
    private List<String> userIds;
}