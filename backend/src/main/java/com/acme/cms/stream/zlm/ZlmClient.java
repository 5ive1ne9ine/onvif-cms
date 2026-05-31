package com.acme.cms.stream.zlm;

import com.acme.cms.config.ZlmProperties;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * ZLMediaKit HTTP API 封装
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZlmClient {

    private final RestTemplate restTemplate;
    private final ZlmProperties zlmProps;

    private String url(String op) {
        return zlmProps.getBaseUrl() + "/index/api/" + op;
    }

    public JSONObject post(String op, Map<String, Object> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", zlmProps.getSecret());
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if (e.getValue() != null) form.add(e.getKey(), String.valueOf(e.getValue()));
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        String resp = restTemplate.postForObject(url(op), entity, String.class);
        log.debug("ZLM {} resp: {}", op, resp);
        return JSON.parseObject(resp);
    }

    /**
     * 添加 RTSP 代理拉流, 返回流 key (供 delStreamProxy 使用)
     */
    public String addStreamProxy(String app, String stream, String rtspUrl,
                                 boolean enableMp4) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("vhost", "__defaultVhost__");
        p.put("app", app);
        p.put("stream", stream);
        p.put("url", rtspUrl);
        p.put("rtp_type", zlmProps.getRtpType());
        p.put("enable_mp4", enableMp4 ? 1 : 0);
        p.put("enable_hls", 0);
        p.put("enable_rtsp", 1);
        p.put("enable_rtmp", 1);
        JSONObject r = post("addStreamProxy", p);
        if (r != null && r.getIntValue("code") == 0) {
            JSONObject data = r.getJSONObject("data");
            return data == null ? null : data.getString("key");
        }
        // 已存在等错误也接受
        log.warn("addStreamProxy: {}", r);
        return null;
    }

    public void delStreamProxy(String key) {
        if (key == null) return;
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("key", key);
        post("delStreamProxy", p);
    }

    public void closeStream(String app, String stream) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("schema", "rtsp");
        p.put("vhost", "__defaultVhost__");
        p.put("app", app);
        p.put("stream", stream);
        p.put("force", 1);
        post("close_streams", p);
    }

    public JSONObject getMediaList(String app, String stream) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("vhost", "__defaultVhost__");
        if (app != null) p.put("app", app);
        if (stream != null) p.put("stream", stream);
        return post("getMediaList", p);
    }

    public boolean isStreamAlive(String app, String stream) {
        JSONObject r = getMediaList(app, stream);
        if (r == null) return false;
        Object data = r.get("data");
        return data instanceof java.util.List && !((java.util.List<?>) data).isEmpty();
    }

    public void startRecord(String app, String stream, String customPath) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("type", 1);
        p.put("vhost", "__defaultVhost__");
        p.put("app", app);
        p.put("stream", stream);
        if (customPath != null) p.put("customized_path", customPath);
        post("startRecord", p);
    }

    public void stopRecord(String app, String stream) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("type", 1);
        p.put("vhost", "__defaultVhost__");
        p.put("app", app);
        p.put("stream", stream);
        post("stopRecord", p);
    }

    public byte[] getSnap(String streamUrl, int timeoutSec, int expireSec) {
        // getSnap 返回的是 image/jpeg 二进制
        try {
            StringBuilder sb = new StringBuilder(url("getSnap"));
            sb.append("?secret=").append(zlmProps.getSecret())
                    .append("&url=").append(java.net.URLEncoder.encode(streamUrl, "UTF-8"))
                    .append("&timeout_sec=").append(timeoutSec)
                    .append("&expire_sec=").append(expireSec);
            ResponseEntity<byte[]> resp = restTemplate.getForEntity(sb.toString(), byte[].class);
            return resp.getBody();
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * WebRTC 信令: 将前端 SDP offer 转发给 ZLM, 取回 SDP answer
     */
    public String webrtcSignal(String app, String stream, String sdpOffer) {
        String u = zlmProps.getBaseUrl() + "/index/api/webrtc"
                + "?app=" + app + "&stream=" + stream
                + "&type=" + zlmProps.getWebrtc().getPlayType();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType("application/sdp"));
        HttpEntity<String> entity = new HttpEntity<>(sdpOffer, h);
        return restTemplate.postForObject(u, entity, String.class);
    }
}
