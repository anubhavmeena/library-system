package com.library.membership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// Read-only sibling of admin-service's AppSettings entity, mapping only the
// columns this service needs (id + convenienceFee + wifi fields for the
// booking-confirmation notification) — admin-service owns writes to this
// singleton row (id=1); this service only ever reads it.
@Entity
@Table(name = "app_settings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AppSettings {

    @Id
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(name = "convenience_fee", precision = 10, scale = 2)
    private BigDecimal convenienceFee;

    @Column(name = "wifi_name")
    private String wifiName;

    @Column(name = "wifi_password")
    private String wifiPassword;
}
