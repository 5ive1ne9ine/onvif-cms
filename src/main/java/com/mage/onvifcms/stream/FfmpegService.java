package com.mage.onvifcms.stream;

import com.mage.onvifcms.api.ApiException;
import com.mage.onvifcms.config.AppProperties;
import com.mage.onvifcms.domain.Camera;
import com.mage.onvifcms.onvif.OnvifSoapClient;
import com.mage.onvifcms.service.CameraService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class FfmpegService {
    private static final int MAX_CAPTURE_BYTES = 32 * 1024 * 1024;
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
        Process process = start(command);
        try (InputStream input = process.getInputStream()) {
            input.transferTo(destination);
        } catch (IOException ignored) {
            // Browser tabs routinely close the response stream; process cleanup happens below.
        } finally {
            stop(process);
        }
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
                "-rtsp_transport", "tcp", "-rw_timeout", "10000000",
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

    private Process start(List<String> command) {
        try {
            return new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "无法启动 FFmpeg：" + exception.getMessage());
        }
    }

    private byte[] capture(List<String> command, Duration timeout) {
        Process process = start(command);
        var outputFuture = executor.submit(() -> readLimited(process.getInputStream(), MAX_CAPTURE_BYTES));
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "读取视频流超时");
            }
            byte[] output = outputFuture.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || output.length == 0) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "FFmpeg 无法读取摄像头视频流");
            }
            return output;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "视频流处理失败：" + exception.getMessage());
        } finally {
            outputFuture.cancel(true);
            stop(process);
        }
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

