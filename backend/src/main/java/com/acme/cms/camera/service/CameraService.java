package com.acme.cms.camera.service;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.camera.mapper.CameraMapper;
import com.acme.cms.camera.onvif.OnvifClientFactory;
import com.acme.cms.camera.onvif.OnvifDeviceInfo;
import com.acme.cms.common.BizException;
import com.acme.cms.common.util.AesUtil;
import com.acme.cms.config.OnvifProperties;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraMapper cameraMapper;
    private final OnvifClientFactory onvifFactory;
    private final OnvifProperties onvifProps;

    public Page<Camera> page(int current, int size, String keyword) {
        Page<Camera> p = new Page<>(current, size);
        QueryWrapper<Camera> q = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            q.like("name", keyword).or().like("ip", keyword);
        }
        q.orderByDesc("id");
        return cameraMapper.selectPage(p, q);
    }

    public Camera get(Long id) {
        Camera c = cameraMapper.selectById(id);
        if (c == null) throw new BizException(404, "Camera not found");
        return c;
    }

    public Camera findByIpPort(String ip, int port) {
        return cameraMapper.selectOne(new QueryWrapper<Camera>()
                .eq("ip", ip).eq("onvif_port", port));
    }

    public List<Camera> listEnabled() {
        return cameraMapper.selectList(new QueryWrapper<Camera>().eq("enabled", 1));
    }

    @Transactional
    public Camera create(Camera input) {
        if (findByIpPort(input.getIp(), input.getOnvifPort()) != null) {
            throw new BizException(409, "Camera with this IP+Port already exists");
        }
        encryptPassword(input);
        if (input.getEnabled() == null) input.setEnabled(true);
        input.setStatus("OFFLINE");
        cameraMapper.insert(input);
        // 异步/同步探测均可, 此处同步以便首次反馈结果
        try {
            refreshCapabilities(input);
        } catch (Exception e) {
            log.warn("Initial probe failed for {}: {}", input.getIp(), e.getMessage());
            input.setStatus("ERROR");
            cameraMapper.updateById(input);
        }
        return cameraMapper.selectById(input.getId());
    }

    @Transactional
    public Camera update(Long id, Camera input) {
        Camera exist = get(id);
        exist.setName(input.getName());
        if (input.getPassword() != null && !input.getPassword().isEmpty()
                && !"******".equals(input.getPassword())) {
            exist.setPassword(input.getPassword());
            encryptPassword(exist);
        }
        if (input.getUsername() != null) exist.setUsername(input.getUsername());
        if (input.getOnvifPort() != null) exist.setOnvifPort(input.getOnvifPort());
        if (input.getEnabled() != null) exist.setEnabled(input.getEnabled());
        cameraMapper.updateById(exist);
        return exist;
    }

    @Transactional
    public void delete(Long id) {
        cameraMapper.deleteById(id);
    }

    /**
     * 调用 ONVIF 接口探测设备能力, 更新 DB 字段
     */
    @Transactional
    public Camera refreshCapabilities(Camera cam) throws Exception {
        OnvifDeviceInfo info = onvifFactory.probe(cam);
        cam.setManufacturer(info.getManufacturer());
        cam.setModel(info.getModel());
        cam.setSerialNo(info.getSerialNumber());
        cam.setFirmware(info.getFirmware());
        cam.setPtzSupported(info.isPtzSupported());
        cam.setEventsSupported(info.isEventsSupported());

        if (info.getProfiles() != null) {
            if (info.getProfiles().size() > 0) cam.setMainRtspUrl(
                    injectAuthIntoRtsp(info.getProfiles().get(0).getRtspUrl(), cam));
            if (info.getProfiles().size() > 1) cam.setSubRtspUrl(
                    injectAuthIntoRtsp(info.getProfiles().get(1).getRtspUrl(), cam));
        }
        cam.setStatus("ONLINE");
        cameraMapper.updateById(cam);
        return cam;
    }

    private void encryptPassword(Camera cam) {
        if (cam.getPassword() != null && !cam.getPassword().isEmpty()) {
            cam.setPassword(AesUtil.encrypt(cam.getPassword(), onvifProps.getAesKey()));
        }
    }

    /**
     * 在 RTSP URL 中注入 user:password, 供 ZLM 拉流使用 (大多数 ONVIF 摄像头 RTSP 需要鉴权)
     */
    private String injectAuthIntoRtsp(String rtsp, Camera cam) {
        if (rtsp == null || rtsp.isEmpty()) return rtsp;
        if (cam.getUsername() == null || cam.getUsername().isEmpty()) return rtsp;
        if (rtsp.contains("@")) return rtsp;
        String plain = AesUtil.decrypt(cam.getPassword(), onvifProps.getAesKey());
        String creds = cam.getUsername() + ":" + (plain == null ? "" : plain) + "@";
        return rtsp.replaceFirst("rtsp://", "rtsp://" + creds);
    }
}
