package com.acme.cms.stream.zlm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 ZLMediaKit 的回调钩子, 用于流状态/录制完成等事件
 * 配置在 ZLM config.ini 的 [hook] 段, 形如 http://cms-host:8080/zlm/hook/on_xxx
 */
@Slf4j
@RestController
@RequestMapping("/zlm/hook")
public class ZlmHookController {

    /** 默认对所有未显式处理的回调返回成功 */
    private static final String OK = "{\"code\":0,\"msg\":\"success\"}";

    @PostMapping("/on_server_keepalive")
    public String keepalive(@RequestBody(required = false) String body) {
        return OK;
    }

    @PostMapping("/on_publish")
    public String onPublish(@RequestBody(required = false) String body) {
        // 默认允许发布, 但不开启 hls/mp4 录制 (由后端按需触发)
        return "{\"code\":0,\"msg\":\"success\",\"enable_mp4\":false,\"enable_hls\":false}";
    }

    @PostMapping("/on_stream_changed")
    public String streamChanged(@RequestBody(required = false) String body) {
        log.info("ZLM stream changed: {}", body);
        return OK;
    }

    @PostMapping("/on_stream_none_reader")
    public String noneReader(@RequestBody(required = false) String body) {
        // 暂不主动关闭, 保持流活着以便事件录制
        return "{\"code\":0,\"close\":false}";
    }

    @PostMapping("/on_record_mp4")
    public String recordMp4(@RequestBody(required = false) String body) {
        log.info("ZLM record mp4 completed: {}", body);
        // 这里可以解析并通知 RecordingService 更新对应 recording 行
        try {
            JSONObject j = JSON.parseObject(body);
            log.debug("Record file path: {}", j == null ? null : j.getString("file_path"));
        } catch (Exception ignore) {}
        return OK;
    }

    @PostMapping("/on_stream_not_found")
    public String notFound(@RequestBody(required = false) String body) {
        return "{\"code\":0,\"msg\":\"success\"}";
    }

    @PostMapping("/on_play")
    public String onPlay(@RequestBody(required = false) String body) {
        return OK;
    }

    @PostMapping("/on_rtsp_realm")
    public String onRtspRealm(@RequestBody(required = false) String body) {
        // 不需要 RTSP 鉴权
        return "{\"code\":0,\"realm\":\"\"}";
    }

    @PostMapping("/on_rtsp_auth")
    public String onRtspAuth(@RequestBody(required = false) String body) {
        return "{\"code\":0,\"encrypted\":false,\"passwd\":\"\"}";
    }

    @PostMapping("/on_shell_login")
    public String onShellLogin(@RequestBody(required = false) String body) {
        return "{\"code\":0,\"msg\":\"success\"}";
    }

    @PostMapping("/on_flow_report")
    public String onFlowReport(@RequestBody(required = false) String body) {
        return OK;
    }

    @PostMapping("/on_http_access")
    public String onHttpAccess(@RequestBody(required = false) String body) {
        return "{\"code\":0,\"err\":\"\",\"path\":\"\",\"second\":600}";
    }

    @PostMapping("/on_record_ts")
    public String onRecordTs(@RequestBody(required = false) String body) {
        return OK;
    }

    @PostMapping("/on_send_rtp_stopped")
    public String onSendRtpStopped(@RequestBody(required = false) String body) {
        return OK;
    }
}
