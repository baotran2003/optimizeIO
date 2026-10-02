package com.example.optimizeIO.lab2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_executions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_campaign_code", columnNames = "campaign_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_code", nullable = false, length = 64)
    private String campaignCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // "TRIGGERED", "DUPLICATE_REJECTED"

    @Version
    private Integer version;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy; // e.g. "Marketing_Alice", "Marketing_Bob"

    @Column(name = "thread_name", length = 64)
    private String threadName;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;
}
