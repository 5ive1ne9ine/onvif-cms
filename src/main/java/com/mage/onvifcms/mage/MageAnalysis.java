package com.mage.onvifcms.mage;

public record MageAnalysis(boolean event, String type, String severity, double confidence,
                           String summary, String rawResponse) {
    public MageAnalysis {
        type = type == null || type.isBlank() ? "其他" : type;
        severity = normalizeSeverity(severity);
        confidence = Math.max(0, Math.min(1, confidence));
        summary = summary == null || summary.isBlank() ? "视觉模型未提供事件说明" : summary;
    }

    private static String normalizeSeverity(String value) {
        if (value == null) return "LOW";
        return switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "CRITICAL", "HIGH", "MEDIUM", "LOW" -> value.toUpperCase(java.util.Locale.ROOT);
            default -> "LOW";
        };
    }
}
