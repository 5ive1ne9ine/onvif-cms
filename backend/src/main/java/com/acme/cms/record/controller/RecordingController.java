package com.acme.cms.record.controller;

import com.acme.cms.camera.service.CameraService;
import com.acme.cms.common.PageResp;
import com.acme.cms.common.R;
import com.acme.cms.record.entity.Recording;
import com.acme.cms.record.service.RecordingService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;
    private final CameraService cameraService;

    @GetMapping
    public R<PageResp<Recording>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Page<Recording> p = recordingService.page(page, size, cameraId, type, from, to);
        return R.ok(PageResp.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        recordingService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/play")
    public ResponseEntity<FileSystemResource> play(@PathVariable Long id) {
        Recording r = recordingService.get(id);
        File f = new File(r.getFilePath());
        if (!f.exists()) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("video/mp4"));
        headers.setContentLength(f.length());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(f));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) throws Exception {
        Recording r = recordingService.get(id);
        File f = new File(r.getFilePath());
        if (!f.exists()) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                + URLEncoder.encode(f.getName(), StandardCharsets.UTF_8.name()) + "\"");
        headers.setContentLength(f.length());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(f));
    }

    @PostMapping("/manual/start")
    public R<Recording> manualStart(@RequestParam Long cameraId,
                                     @RequestParam(defaultValue = "300") int maxSeconds) throws Exception {
        // 异步触发, 最长 maxSeconds 后自动停止 (防止意外永久录制)
        return R.ok(recordingService.recordClip(cameraService.get(cameraId), maxSeconds, "MANUAL", null));
    }

    @PostMapping("/manual/{cameraId}/stop")
    public R<Recording> manualStop(@PathVariable Long cameraId) {
        return R.ok(recordingService.stopManual(cameraId));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<FileSystemResource> snapshot(@RequestParam String path) {
        File f = new File(path);
        if (!f.exists()) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setContentLength(f.length());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(f));
    }
}
