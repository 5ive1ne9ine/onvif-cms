package com.acme.cms.stream.controller;

import com.acme.cms.common.R;
import com.acme.cms.stream.service.StreamService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    @PostMapping("/{cameraId}/start")
    public R<StreamService.StreamInfo> start(@PathVariable Long cameraId) {
        return R.ok(streamService.ensureProxy(cameraId));
    }

    @PostMapping("/{cameraId}/stop")
    public R<Void> stop(@PathVariable Long cameraId) {
        streamService.stop(cameraId);
        return R.ok();
    }

    /**
     * WebRTC 信令: 接收前端 SDP offer, 返回 SDP answer
     * 前端调用方式: 发送 JSON {sdp, type:"offer"}, 服务端响应 {sdp, type:"answer"}
     */
    @PostMapping(value = "/{cameraId}/webrtc/offer",
            consumes = {MediaType.APPLICATION_JSON_VALUE, "application/sdp"})
    public R<Object> signal(@PathVariable Long cameraId, @RequestBody String body) {
        String sdp;
        boolean wrapJson = true;
        if (body != null && body.trim().startsWith("{")) {
            JSONObject j = JSON.parseObject(body);
            sdp = j.getString("sdp");
        } else {
            sdp = body;
            wrapJson = false;
        }
        String answerRaw = streamService.webrtcSignal(cameraId, sdp);

        // ZLM 返回的可能是 JSON ({sdp, type:"answer", code:0}) 或纯 SDP, 视版本而定
        if (answerRaw != null && answerRaw.trim().startsWith("{")) {
            return R.ok(JSON.parse(answerRaw));
        }
        // 纯 SDP - 包装一下
        SdpAnswer ans = new SdpAnswer();
        ans.setSdp(answerRaw);
        ans.setType("answer");
        return R.ok(ans);
    }

    @Data
    public static class SdpAnswer {
        private String sdp;
        private String type;
    }
}
