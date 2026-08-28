package com.mage.onvifcms.stream;

import com.mage.onvifcms.api.ApiException;
import com.mage.onvifcms.config.AppProperties;
import com.mage.onvifcms.domain.Camera;
import com.mage.onvifcms.onvif.OnvifSoapClient;
import com.mage.onvifcms.service.CameraService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegService {
    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);
    private static final int MAX_CAPTURE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ERROR_BYTES = 64 * 1024;
    private final AppProperties properties;
    private final CameraService cameras;
    private final ExecutorService executor;

    public FfmpegService(AppProperties properties, CameraService cameras, ExecutorService executor) {
        this.properties = properties;
        this.cameras = cameras;
        this.executor = executor;
    }

    public void streamMjpeg(Long cameraId, OutputStream destination) {
        Camera camera = requireStream(cameraId);
        List<String> command = baseInput(camera);
        command.addAll(List.of(
                "-an", "-vf", "fps=" + properties.ffmpeg().previewFps() +
                        ",scale=" + properties.ffmpeg().previewWidth() + ":-2:force_original_aspect_ratio=decrease",
                "-q:v", "5", "-f", "mpjpeg", "-boundary_tag", "frame", "pipe:1"));
        Process process = start(command, true);
        try (InputStream input = process.getInputStream()) {
            input.transferTo(destination);
        } catch (IOException ignored) {
            // Browser tabs routinely close the response stream; process cleanup happens below.
        } finally {
            stop(process);
        }
    }

    public void validateStream(Long cameraId) {
        requireStream(cameraId);
    }

    public byte[] snapshot(Long cameraId) {
        Camera camera = requireStream(cameraId);
        List<String> command = baseInput(camera);
        command.addAll(List.of("-an", "-frames:v", "1", "-q:v", "3", "-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1"));
        return capture(command, Duration.ofSeconds(20));
    }

    public List<byte[]> sampleFrames(Camera camera) {
        int seconds = properties.ffmpeg().sampleSeconds();
        int maxFrames = properties.mage().maxFrames();
        double fps = Math.max(0.1, (double) maxFrames / seconds);
        List<String> command = baseInput(camera);
        command.addAll(List.of(
                "-an", "-t", Integer.toString(seconds),
                "-vf", "fps=" + String.format(java.util.Locale.ROOT, "%.3f", fps) +
                        ",scale=640:-2:force_original_aspect_ratio=decrease",
                "-frames:v", Integer.toString(maxFrames), "-q:v", "5",
                "-f", "image2pipe", "-vcodec", "mjpeg", "pipe:1"));
        byte[] stream = capture(command, Duration.ofSeconds(seconds + 20L));
        List<byte[]> frames = splitJpegs(stream, maxFrames);
        if (frames.isEmpty()) throw new ApiException(HttpStatus.BAD_GATEWAY, "FFmpeg 未能从视频流提取画面");
        return frames;
    }

    public boolean available() {
        Process process = null;
        try {
            process = new ProcessBuilder(properties.ffmpeg().executable(), "-version")
                    .redirectErrorStream(true).start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private Camera requireStream(Long cameraId) {
        Camera camera = cameras.get(cameraId);
        if (camera.getRtspUri() == null || camera.getRtspUri().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "摄像头尚未取得 RTSP 地址，请先配置账号");
        }
        if (!available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "未找到 FFmpeg，请安装或配置 FFMPEG_PATH");
        return camera;
    }

    private List<String> baseInput(Camera camera) {
        OnvifSoapClient.Credentials credentials = cameras.credentials(camera);
        return new ArrayList<>(List.of(
                properties.ffmpeg().executable(), "-hide_banner", "-loglevel", "error",
                "-rtsp_transport", "tcp", "-timeout", "10000000",
                "-i", streamUri(camera.getRtspUri(), credentials)));
    }

    private String streamUri(String rawUri, OnvifSoapClient.Credentials credentials) {
        if (credentials.username().isBlank()) return rawUri;
        try {
            URI uri = URI.create(rawUri);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) return rawUri;
            String userInfo = credentials.username() + ":" + credentials.password();
            return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(),
                    uri.getQuery(), uri.getFragment()).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "摄像头返回了无效的 RTSP 地址");
        }
    }

    private Process start(List<String> command, boolean discardError) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (discardError) builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            return builder.start();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "无法启动 FFmpeg：" + exception.getMessage());
        }
    }

    private byte[] capture(List<String> command, Duration timeout) {
        Process process = start(command, false);
        var outputFuture = executor.submit(() -> readLimited(process.getInputStream(), MAX_CAPTURE_BYTES));
        var errorFuture = executor.submit(() -> readLimited(process.getErrorStream(), MAX_ERROR_BYTES));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "读取视频流超时");
            }
            byte[] output = outputFuture.get(3, TimeUnit.SECONDS);
            byte[] error = errorFuture.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || output.length == 0) {
                log.warn("FFmpeg capture failed: {}", sanitizeFailure(error));
                throw new ApiException(HttpStatus.BAD_GATEWAY, describeFailure(error));
            }
            return output;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "视频流处理失败：" + exception.getMessage());
        } finally {
            outputFuture.cancel(true);
            errorFuture.cancel(true);
            stop(process);
        }
    }

    static String describeFailure(byte[] error) {
        String detail = new String(error, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
        if (detail.contains("401 unauthorized") || detail.contains("authentication failed")) {
            return "RTSP 认证失败，请检查摄像头账号或密码";
        }
        if (detail.contains("404 not found")) {
            return "摄像头返回的 RTSP 路径不存在";
        }
        if (detail.contains("connection refused")) {
            return "摄像头拒绝 RTSP 连接，请确认 RTSP 服务与端口已启用";
        }
        if (detail.contains("timed out") || detail.contains("timeout")) {
            return "连接摄像头 RTSP 服务超时";
        }
        if (detail.contains("invalid data found") || detail.contains("unsupported codec")) {
            return "RTSP 视频编码无法解码，请检查摄像头编码格式";
        }
        return "FFmpeg 无法读取摄像头视频流";
    }

    static String sanitizeFailure(byte[] error) {
        String detail = new String(error, StandardCharsets.UTF_8)
                .replaceAll("(?i)(rtsp://)[^\\s/@]+(?::[^\\s/@]*)?@", "$1***@")
                .replaceAll("(?i)(password|pwd)=([^\\s&]+)", "$1=***")
                .strip();
        return detail.length() <= 2000 ? detail : detail.substring(detail.length() - 2000);
    }

    private byte[] readLimited(InputStream input, int limit) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IOException("视频采样数据超过限制");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    static List<byte[]> splitJpegs(byte[] data, int maxFrames) {
        List<byte[]> frames = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < data.length - 1 && frames.size() < maxFrames; i++) {
            if (start < 0 && (data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == 0xd8) {
                start = i;
                i++;
            } else if (start >= 0 && (data[i] & 0xff) == 0xff && (data[i + 1] & 0xff) == 0xd9) {
                frames.add(Arrays.copyOfRange(data, start, i + 2));
                start = -1;
                i++;
            }
        }
        return frames;
    }

    private void stop(Process process) {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
