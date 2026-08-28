package com.mage.onvifcms.mage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mage.onvifcms.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

class MageClientTest {
    private final MageClient client = new MageClient(
            new AppProperties(
                    new AppProperties.Discovery(30, 1000),
                    new AppProperties.Ffmpeg("ffmpeg", 8, 1280, 8),
                    new AppProperties.Mage(true, "http://localhost:30000/v1", "microsoft/Mage-VL", 6, 2, 60),
                    new AppProperties.Security("test-key")),
            HttpClient.newHttpClient(), new ObjectMapper());

    @Test
    void parsesJsonInsideMarkdownFence() {
        MageAnalysis result = client.parseModelOutput("""
                ```json
                {"event":true,"type":"人员跌倒","severity":"HIGH","confidence":0.91,"summary":"一人倒地后未起身"}
                ```
                """);

        assertThat(result.event()).isTrue();
        assertThat(result.type()).isEqualTo("人员跌倒");
        assertThat(result.severity()).isEqualTo("HIGH");
        assertThat(result.confidence()).isEqualTo(0.91);
    }

    @Test
    void clampsUntrustedModelValues() {
        MageAnalysis result = client.parseModelOutput(
                "{\"event\":true,\"type\":\"test\",\"severity\":\"unknown\",\"confidence\":2,\"summary\":\"ok\"}");

        assertThat(result.severity()).isEqualTo("LOW");
        assertThat(result.confidence()).isEqualTo(1);
    }
}

