package com.n8n.mobile.studio.security

enum class RiskLevel { READ, LOW_RISK_WRITE, HIGH_RISK_WRITE, DESTRUCTIVE }

class RiskPolicy {
    fun requiresConfirmation(level: RiskLevel): Boolean = level >= RiskLevel.HIGH_RISK_WRITE
}
