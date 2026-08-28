package com.mage.onvifcms.stream;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegServiceTest {

    @Test
    void splitsConcatenatedJpegFrames() {
        byte[] first = {(byte) 0xff, (byte) 0xd8, 1, 2, (byte) 0xff, (byte) 0xd9};
        byte[] second = {(byte) 0xff, (byte) 0xd8, 3, 4, 5, (byte) 0xff, (byte) 0xd9};
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.writeBytes(first);
        stream.writeBytes(second);

        List<byte[]> frames = FfmpegService.splitJpegs(stream.toByteArray(), 8);

        assertThat(frames).containsExactly(first, second);
    }

    @Test
    void observesFrameLimit() {
        byte[] stream = {(byte) 0xff, (byte) 0xd8, 1, (byte) 0xff, (byte) 0xd9,
                (byte) 0xff, (byte) 0xd8, 2, (byte) 0xff, (byte) 0xd9};
        assertThat(FfmpegService.splitJpegs(stream, 1)).hasSize(1);
    }

    @Test
    void explainsRtspAuthenticationFailureWithoutExposingFfmpegOutput() {
        byte[] error = "method DESCRIBE failed: 401 Unauthorized\nrtsp://admin:secret@camera/stream"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(FfmpegService.describeFailure(error))
                .isEqualTo("RTSP 认证失败，请检查摄像头账号或密码")
                .doesNotContain("secret");
    }

    @Test
    void redactsCredentialsFromFfmpegDiagnostics() {
        byte[] error = "Failed to open rtsp://admin:secret@192.168.1.2/stream?password=other"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(FfmpegService.sanitizeFailure(error))
                .contains("rtsp://***@192.168.1.2/stream?password=***")
                .doesNotContain("secret", "other");
    }
}
