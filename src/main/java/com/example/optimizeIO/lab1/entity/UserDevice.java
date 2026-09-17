package com.example.optimizeIO.lab1.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_token", nullable = false, length = 128)
    private String deviceToken; // định danh duy nhất 1 thiết bị của user đó

    @Column(name = "os_type", nullable = false, length = 16)
    private String osType; // "IOS" or "ANDROID"

    @Column(name = "created_at")
    private LocalDateTime createdAt;


}
