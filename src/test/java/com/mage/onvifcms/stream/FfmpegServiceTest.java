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
}

