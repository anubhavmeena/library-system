package com.library.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Singleton config row — always id=1, never a second row. No @GeneratedValue:
// the id is always explicitly set to 1L by AppSettingsService.
@Entity
@Table(name = "app_settings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AppSettings {

    @Id
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(name = "wifi_name")
    private String wifiName;

    @Column(name = "wifi_password")
    private String wifiPassword;

    @Column(name = "grace_days", nullable = false)
    private Integer graceDays;

    @Column(name = "convenience_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal convenienceFee;

    @Column(name = "water_tanker_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal waterTankerRate;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
