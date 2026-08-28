package com.mage.onvifcms.api;

import com.mage.onvifcms.stream.FfmpegService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/cameras/{cameraId}")
public class StreamController {
    private final FfmpegService ffmpeg;

    public StreamController(FfmpegService ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    @GetMapping(value = "/preview.mjpg", produces = "multipart/x-mixed-replace;boundary=frame")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable("cameraId") Long cameraId) {
        ffmpeg.validateStream(cameraId);
        StreamingResponseBody body = output -> ffmpeg.streamMjpeg(cameraId, output);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.parseMediaType("multipart/x-mixed-replace;boundary=frame"))
                .body(body);
    }

    @GetMapping(value = "/snapshot.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> snapshot(@PathVariable("cameraId") Long cameraId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ffmpeg.snapshot(cameraId));
    }
}
