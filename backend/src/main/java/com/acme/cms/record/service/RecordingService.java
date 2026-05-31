package com.acme.cms.record.service;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.camera.service.CameraService;
import com.acme.cms.common.BizException;
import com.acme.cms.common.util.AesUtil;
import com.acme.cms.config.FfmpegProperties;
import com.acme.cms.config.OnvifProperties;
import com.acme.cms.config.StorageProperties;
import com.acme.cms.record.entity.Recording;
import com.acme.cms.record.mapper.RecordingMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final RecordingMapper mapper;
    private final CameraService cameraService;
    private final StorageProperties storageProps;
    private final OnvifProperties onvifProps;
    private final FfmpegProperties ffmpegProps;

    /** cameraId -> 进行中的 FFmpeg 进程 (用于手动停止) */
    private final ConcurrentMap<Long, Process> manualProcesses = new ConcurrentHashMap<>();

    public Page<Recording> page(int current, int size, Long cameraId, String type,
                                 LocalDateTime from, LocalDateTime to) {
        Page<Recording> p = new Page<>(current, size);
        QueryWrapper<Recording> q = new QueryWrapper<>();
        if (cameraId != null) q.eq("camera_id", cameraId);
        if (type != null) q.eq("type", type);
        if (from != null) q.ge("start_time", from);
        if (to != null) q.le("start_time", to);
        q.orderByDesc("start_time");
        return mapper.selectPage(p, q);
    }

    public Recording get(Long id) {
        Recording r = mapper.selectById(id);
        if (r == null) throw new BizException(404, "Recording not found");
        return r;
    }

    public void delete(Long id) {
        Recording r = get(id);
        try {
            Files.deleteIfExists(Paths.get(r.getFilePath()));
        } catch (Exception e) {
            log.warn("Delete file {} failed: {}", r.getFilePath(), e.getMessage());
        }
        mapper.deleteById(id);
    }

    public Recording insert(Recording r) {
        mapper.insert(r);
        return r;
    }

    public void update(Recording r) {
        mapper.updateById(r);
    }

    /**
     * 通过 FFmpeg 录制指定秒数的视频片段
     */
    public Recording recordClip(Camera cam, int durationSeconds, String type, Long eventId)
            throws Exception {
        String rtsp = decryptedRtsp(cam);
        if (rtsp == null) throw new BizException(400, "Camera RTSP is not available");

        Path dir = Paths.get(storageProps.getRecordDir(),
                "cam_" + cam.getId(),
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        Files.createDirectories(dir);
        String fileName = (type == null ? "rec" : type.toLowerCase()) + "_"
                + System.currentTimeMillis() + ".mp4";
        Path file = dir.resolve(fileName);

        Recording r = new Recording();
        r.setCameraId(cam.getId());
        r.setType(type);
        r.setFilePath(file.toString());
        r.setStartTime(LocalDateTime.now());
        r.setStatus("RECORDING");
        r.setEventId(eventId);
        mapper.insert(r);

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegProps.getBin(),
                "-hide_banner", "-loglevel", "warning",
                "-rtsp_transport", "tcp",
                "-i", rtsp,
                "-t", String.valueOf(durationSeconds),
                "-c", "copy",
                "-y", file.toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        if ("MANUAL".equals(type) && eventId == null) {
            // 手动录制: 不阻塞调用线程, 进程异步运行直到 maxSeconds 或被停止
            manualProcesses.put(cam.getId(), p);
            final Recording recCopy = r;
            new Thread(() -> {
                try {
                    p.waitFor();
                    finishRecording(recCopy, file.toFile());
                    manualProcesses.remove(cam.getId(), p);
                } catch (InterruptedException ignore) {}
            }, "ffmpeg-manual-" + cam.getId()).start();
        } else {
            p.waitFor();
            finishRecording(r, file.toFile());
        }
        return r;
    }

    public Recording stopManual(Long cameraId) {
        Process p = manualProcesses.remove(cameraId);
        if (p != null && p.isAlive()) {
            p.destroy();
            try { p.waitFor(); } catch (InterruptedException ignore) {}
        }
        // 查 status=RECORDING 且 type=MANUAL 的最新一条
        QueryWrapper<Recording> q = new QueryWrapper<Recording>()
                .eq("camera_id", cameraId).eq("type", "MANUAL")
                .eq("status", "RECORDING").orderByDesc("id").last("limit 1");
        Recording r = mapper.selectOne(q);
        if (r != null) {
            finishRecording(r, new File(r.getFilePath()));
        }
        return r;
    }

    private void finishRecording(Recording r, File file) {
        r.setEndTime(LocalDateTime.now());
        r.setDurationMs(java.time.Duration.between(r.getStartTime(), r.getEndTime()).toMillis());
        if (file.exists() && file.length() > 0) {
            r.setFileSize(file.length());
            r.setStatus("COMPLETED");
        } else {
            r.setStatus("FAILED");
        }
        mapper.updateById(r);
    }

    public String decryptedRtsp(Camera cam) {
        // CameraService 在保存时已注入 user:password 到 mainRtspUrl,
        // 但 password 部分是 AES 密文, 需要在使用时解密.
        // 这里简化: 直接取 mainRtspUrl, 如果带 ":AESxxx@", 替换为明文.
        String rtsp = cam.getMainRtspUrl();
        if (rtsp == null) return null;
        if (cam.getUsername() == null) return rtsp;
        // 注入逻辑使用了明文 (CameraService.injectAuthIntoRtsp 立即解密), 这里直接返回
        return rtsp;
    }
}
