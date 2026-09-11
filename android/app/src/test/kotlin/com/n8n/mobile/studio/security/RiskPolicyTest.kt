package com.n8n.mobile.studio.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskPolicyTest {
    private val policy = RiskPolicy()

    @Test fun readDoesNotRequireConfirmation() = assertFalse(policy.requiresConfirmation(RiskLevel.READ))

    @Test fun lowRiskWriteDoesNotRequireConfirmation() = assertFalse(policy.requiresConfirmation(RiskLevel.LOW_RISK_WRITE))

    @Test fun highRiskWriteRequiresConfirmation() = assertTrue(policy.requiresConfirmation(RiskLevel.HIGH_RISK_WRITE))

    @Test fun destructiveRequiresConfirmation() = assertTrue(policy.requiresConfirmation(RiskLevel.DESTRUCTIVE))
}
