package com.mage.onvifcms.api;

import com.mage.onvifcms.service.CameraService;
import com.mage.onvifcms.service.DiscoveryService;
import com.mage.onvifcms.service.PtzService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CameraController {
    private final CameraService cameras;
    private final DiscoveryService discovery;
    private final PtzService ptz;

    public CameraController(CameraService cameras, DiscoveryService discovery, PtzService ptz) {
        this.cameras = cameras;
        this.discovery = discovery;
        this.ptz = ptz;
    }

    @GetMapping("/cameras")
    public List<CameraView> cameras() {
        return cameras.list();
    }

    @PostMapping("/discovery/scan")
    public List<CameraView> discover() {
        return discovery.scan();
    }

    @PutMapping("/cameras/{id}/credentials")
    public CameraView credentials(@PathVariable("id") Long id, @Valid @RequestBody CredentialsRequest request) {
        return cameras.configure(id, request.name(), request.username(), request.password());
    }

    @PostMapping("/cameras/{id}/connect")
    public CameraView reconnect(@PathVariable("id") Long id) {
        return cameras.reconnect(id);
    }

    @PutMapping("/cameras/{id}/detection")
    public CameraView detection(@PathVariable("id") Long id, @Valid @RequestBody DetectionRequest request) {
        return cameras.configureDetection(id, request.enabled(), request.prompt(), request.intervalSeconds(),
                request.confidenceThreshold());
    }

    @PostMapping("/cameras/{id}/ptz/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(@PathVariable("id") Long id, @Valid @RequestBody PtzMoveRequest request) {
        ptz.move(id, request.pan(), request.tilt(), request.zoom(), request.durationMillis());
    }

    @PostMapping("/cameras/{id}/ptz/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable("id") Long id) {
        ptz.stop(id);
    }

    public record CredentialsRequest(
            @Size(max = 160) String name,
            @Size(max = 160) String username,
            @Size(max = 512) String password) {}

    public record DetectionRequest(
            boolean enabled,
            @Size(max = 4000) String prompt,
            @Min(10) @Max(3600) int intervalSeconds,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidenceThreshold) {}

    public record PtzMoveRequest(
            @DecimalMin("-1.0") @DecimalMax("1.0") double pan,
            @DecimalMin("-1.0") @DecimalMax("1.0") double tilt,
            @DecimalMin("-1.0") @DecimalMax("1.0") double zoom,
            @Min(100) @Max(3000) int durationMillis) {}
}
