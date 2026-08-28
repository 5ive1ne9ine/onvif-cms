package com.mage.onvifcms.onvif;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WsDiscoveryClientTest {

    @Test
    void parsesOnvifProbeMatch() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                  xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                  xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery">
                  <s:Body><d:ProbeMatches><d:ProbeMatch>
                    <a:EndpointReference><a:Address>urn:uuid:camera-1</a:Address></a:EndpointReference>
                    <d:Types>dn:NetworkVideoTransmitter</d:Types>
                    <d:Scopes>onvif://www.onvif.org/name/FrontDoor onvif://www.onvif.org/Profile/Streaming</d:Scopes>
                    <d:XAddrs>http://192.168.100.20/onvif/device_service http://bad host</d:XAddrs>
                  </d:ProbeMatch></d:ProbeMatches></s:Body>
                </s:Envelope>
                """;

        List<DiscoveredDevice> devices = WsDiscoveryClient.parseResponse(xml);

        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).endpointUrn()).isEqualTo("urn:uuid:camera-1");
        assertThat(devices.get(0).host()).isEqualTo("192.168.100.20");
        assertThat(devices.get(0).scopes()).contains("onvif://www.onvif.org/name/FrontDoor");
    }
}
