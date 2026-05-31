package com.acme.cms.camera.controller;

import com.acme.cms.camera.service.PtzService;
import com.acme.cms.common.R;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@RestController
@RequestMapping("/api/cameras/{cameraId}/ptz")
@RequiredArgsConstructor
public class PtzController {

    private final PtzService ptzService;

    @PostMapping("/continuous")
    public R<Void> continuous(@PathVariable Long cameraId,
                              @RequestBody @Valid ContinuousReq req) throws Exception {
        ptzService.continuousMove(cameraId, req.panSpeed, req.tiltSpeed,
                req.zoomSpeed, req.timeoutMs);
        return R.ok();
    }

    @PostMapping("/stop")
    public R<Void> stop(@PathVariable Long cameraId,
                        @RequestBody(required = false) StopReq req) throws Exception {
        boolean pt = req != null ? req.panTilt : true;
        boolean z = req != null ? req.zoom : true;
        ptzService.stop(cameraId, pt, z);
        return R.ok();
    }

    @PostMapping("/relative")
    public R<Void> relative(@PathVariable Long cameraId,
                            @RequestBody @Valid MoveReq req) throws Exception {
        ptzService.relativeMove(cameraId, req.pan, req.tilt, req.zoom);
        return R.ok();
    }

    @PostMapping("/absolute")
    public R<Void> absolute(@PathVariable Long cameraId,
                            @RequestBody @Valid MoveReq req) throws Exception {
        ptzService.absoluteMove(cameraId, req.pan, req.tilt, req.zoom);
        return R.ok();
    }

    @GetMapping("/presets")
    public R<String> presets(@PathVariable Long cameraId) throws Exception {
        return R.ok(ptzService.getPresets(cameraId));
    }

    @PostMapping("/presets/{token}/goto")
    public R<Void> gotoPreset(@PathVariable Long cameraId,
                              @PathVariable String token) throws Exception {
        ptzService.gotoPreset(cameraId, token);
        return R.ok();
    }

    @PostMapping("/presets")
    public R<String> setPreset(@PathVariable Long cameraId,
                               @RequestBody PresetReq req) throws Exception {
        String token = ptzService.setPreset(cameraId, req.name);
        return R.ok(token);
    }

    // --- DTOs ---

    @Data
    public static class ContinuousReq {
        @Min(-1) @Max(1)
        private float panSpeed = 0;
        @Min(-1) @Max(1)
        private float tiltSpeed = 0;
        @Min(-1) @Max(1)
        private float zoomSpeed = 0;
        private int timeoutMs = 0;
    }

    @Data
    public static class StopReq {
        private boolean panTilt = true;
        private boolean zoom = true;
    }

    @Data
    public static class MoveReq {
        @Min(-1) @Max(1)
        private float pan = 0;
        @Min(-1) @Max(1)
        private float tilt = 0;
        @Min(-1) @Max(1)
        private float zoom = 0;
    }

    @Data
    public static class PresetReq {
        private String name;
    }
}
