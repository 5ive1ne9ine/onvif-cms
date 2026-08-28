package com.mage.onvifcms.service;

import com.mage.onvifcms.api.ApiException;
import com.mage.onvifcms.domain.Camera;
import com.mage.onvifcms.onvif.OnvifException;
import com.mage.onvifcms.onvif.OnvifSoapClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;

@Service
public class PtzService {
    private final CameraService cameras;
    private final OnvifSoapClient onvif;
    private final ExecutorService executor;

    public PtzService(CameraService cameras, OnvifSoapClient onvif, ExecutorService executor) {
        this.cameras = cameras;
        this.onvif = onvif;
        this.executor = executor;
    }

    public void move(Long cameraId, double pan, double tilt, double zoom, int durationMillis) {
        Camera camera = requirePtz(cameraId);
        try {
            onvif.continuousMove(camera.getPtzServiceUrl(), camera.getProfileToken(), cameras.credentials(camera),
                    clamp(pan), clamp(tilt), clamp(zoom));
            int duration = Math.max(100, Math.min(3000, durationMillis));
            executor.submit(() -> {
                try {
                    Thread.sleep(duration);
                    stop(cameraId);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                    // A later command may have stopped the camera already.
                }
            });
        } catch (OnvifException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    public void stop(Long cameraId) {
        Camera camera = requirePtz(cameraId);
        try {
            onvif.stop(camera.getPtzServiceUrl(), camera.getProfileToken(), cameras.credentials(camera));
        } catch (OnvifException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, exception.getMessage());
        }
    }

    private Camera requirePtz(Long cameraId) {
        Camera camera = cameras.get(cameraId);
        if (!camera.isPtzSupported() || camera.getPtzServiceUrl() == null || camera.getProfileToken() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "该摄像头或当前媒体 Profile 不支持 PTZ");
        }
        return camera;
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) throw new ApiException(HttpStatus.BAD_REQUEST, "PTZ 速度必须是有限数字");
        return Math.max(-1, Math.min(1, value));
    }
}

