package com.servicedesk.remediation;

import com.servicedesk.common.BaseEntity;
import com.servicedesk.user.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A predefined, safe remediation action the platform is allowed to run.
 * This catalog IS the safety boundary: the AI can only ever recommend one
 * of these codes (validated in AnthropicAiProvider/MockAiProvider), and the
 * executor can only ever run a row that exists here and is enabled. There
 * is no code path anywhere that lets the AI or a user supply an arbitrary
 * command - see RemediationExecutor.
 */
@Entity
@Table(name = "remediation_actions", indexes = {
        @Index(name = "idx_remediation_actions_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationAction extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role requiredRole;

    @Builder.Default
    private boolean approvalRequired = true;

    @Builder.Default
    private int timeoutSeconds = 30;

    @Builder.Default
    private int retryLimit = 2;

    @Builder.Default
    private boolean enabled = true;
}
