package com.acme.cms.camera.service;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.camera.onvif.OnvifClientFactory;
import com.acme.cms.camera.onvif.OnvifDeviceInfo;
import com.acme.cms.camera.onvif.OnvifSoapClient;
import com.acme.cms.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.NodeList;

/**
 * PTZ 控制: Continuous / Relative / Absolute / Presets
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PtzService {

    private final OnvifClientFactory onvifFactory;
    private final CameraService cameraService;

    /**
     * 获取摄像头第一个 profile 的 token 和 PTZ 配置 token
     */
    private ProfileInfo getProfileInfo(Camera cam) throws Exception {
        OnvifDeviceInfo info = onvifFactory.probe(cam);
        if (info.getProfiles() == null || info.getProfiles().isEmpty()) {
            throw new BizException(400, "No media profiles found");
        }
        OnvifDeviceInfo.Profile p = info.getProfiles().get(0);
        return new ProfileInfo(p.getToken(), p.getPtzConfigToken());
    }

    /**
     * Continuous move
     */
    public void continuousMove(Long cameraId, float panSpeed, float tiltSpeed,
                               float zoomSpeed, int timeoutMs) throws Exception {
        Camera cam = cameraService.get(cameraId);
        if (!Boolean.TRUE.equals(cam.getPtzSupported())) {
            throw new BizException(400, "PTZ not supported by this camera");
        }
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:ContinuousMove>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + "<tptz:Velocity>"
                + "<tt:PanTilt x=\"" + clamp(panSpeed) + "\" y=\"" + clamp(tiltSpeed) + "\""
                + " space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/VelocityGenericSpace\"/>"
                + "<tt:Zoom x=\"" + clamp(zoomSpeed) + "\""
                + " space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/VelocityGenericSpace\"/>"
                + "</tptz:Velocity>"
                + (timeoutMs > 0 ? "<tptz:Timeout>PT" + (timeoutMs / 1000) + "S</tptz:Timeout>" : "")
                + "</tptz:ContinuousMove>";

        client.call("http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove", body, true);
    }

    /**
     * Stop PTZ movement
     */
    public void stop(Long cameraId, boolean panTilt, boolean zoom) throws Exception {
        Camera cam = cameraService.get(cameraId);
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:Stop>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + "<tptz:PanTilt>" + panTilt + "</tptz:PanTilt>"
                + "<tptz:Zoom>" + zoom + "</tptz:Zoom>"
                + "</tptz:Stop>";
        client.call("http://www.onvif.org/ver20/ptz/wsdl/Stop", body, true);
    }

    /**
     * Relative move
     */
    public void relativeMove(Long cameraId, float pan, float tilt, float zoom) throws Exception {
        Camera cam = cameraService.get(cameraId);
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:RelativeMove>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + "<tptz:Translation>"
                + "<tt:PanTilt x=\"" + clamp(pan) + "\" y=\"" + clamp(tilt) + "\""
                + " space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/TranslationGenericSpace\"/>"
                + "<tt:Zoom x=\"" + clamp(zoom) + "\""
                + " space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/TranslationGenericSpace\"/>"
                + "</tptz:Translation>"
                + "</tptz:RelativeMove>";
        client.call("http://www.onvif.org/ver20/ptz/wsdl/RelativeMove", body, true);
    }

    /**
     * Absolute move
     */
    public void absoluteMove(Long cameraId, float pan, float tilt, float zoom) throws Exception {
        Camera cam = cameraService.get(cameraId);
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:AbsoluteMove>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + "<tptz:Position>"
                + "<tt:PanTilt x=\"" + clamp(pan) + "\" y=\"" + clamp(tilt) + "\""
                + " space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace\"/>"
                + "<tt:Zoom x=\"" + clamp(zoom) + "\""
                + " space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/PositionGenericSpace\"/>"
                + "</tptz:Position>"
                + "</tptz:AbsoluteMove>";
        client.call("http://www.onvif.org/ver20/ptz/wsdl/AbsoluteMove", body, true);
    }

    /**
     * Go to preset
     */
    public void gotoPreset(Long cameraId, String presetToken) throws Exception {
        Camera cam = cameraService.get(cameraId);
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:GotoPreset>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + "<tptz:PresetToken>" + presetToken + "</tptz:PresetToken>"
                + "</tptz:GotoPreset>";
        client.call("http://www.onvif.org/ver20/ptz/wsdl/GotoPreset", body, true);
    }

    /**
     * Set preset at current position
     */
    public String setPreset(Long cameraId, String presetName) throws Exception {
        Camera cam = cameraService.get(cameraId);
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:SetPreset>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + (presetName != null ? "<tptz:PresetName>" + presetName + "</tptz:PresetName>" : "")
                + "</tptz:SetPreset>";
        String resp = client.call("http://www.onvif.org/ver20/ptz/wsdl/SetPreset", body, true);
        org.w3c.dom.Document d = OnvifSoapClient.parseXml(resp);
        NodeList nl = d.getElementsByTagNameNS("*", "PresetToken");
        return nl.getLength() > 0 ? nl.item(0).getTextContent() : null;
    }

    /**
     * Get presets
     */
    public String getPresets(Long cameraId) throws Exception {
        Camera cam = cameraService.get(cameraId);
        ProfileInfo pi = getProfileInfo(cam);
        String ptzXAddr = getPtzXAddr(cam);
        OnvifSoapClient client = onvifFactory.client(cam, ptzXAddr);

        String body = "<tptz:GetPresets>"
                + "<tptz:ProfileToken>" + pi.profileToken + "</tptz:ProfileToken>"
                + "</tptz:GetPresets>";
        return client.call("http://www.onvif.org/ver20/ptz/wsdl/GetPresets", body, true);
    }

    // --- helpers ---

    private String getPtzXAddr(Camera cam) throws Exception {
        OnvifDeviceInfo info = onvifFactory.probe(cam);
        if (info.getPtzXAddr() == null) {
            throw new BizException(400, "PTZ service not available");
        }
        return OnvifClientFactory.sanitizeXaddr(cam.getIp(), info.getPtzXAddr());
    }

    private static float clamp(float v) {
        if (v > 1f) return 1f;
        if (v < -1f) return -1f;
        return v;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ProfileInfo {
        private String profileToken;
        private String ptzConfigToken;
    }
}
