package com.mage.onvifcms.onvif;

import java.util.List;

public record DiscoveredDevice(String endpointUrn, String deviceServiceUrl, String host,
                               List<String> scopes, List<String> types) {
    public String stableKey() {
        return endpointUrn == null || endpointUrn.isBlank() ? deviceServiceUrl : endpointUrn;
    }
}

