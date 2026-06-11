package com.acme.cms.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "zlm")
public class ZlmProperties {
    private String baseUrl;
    private String secret;
    private String defaultApp = "live";
    private int rtpType = 0;
    private String hookToken = "internal";
    private Webrtc webrtc = new Webrtc();

    @Data
    public static class Webrtc {
        /**
         * WebRTC 对外可见 IP，留空则自动检测本机非回环 IP，同机部署无需配置
         */
        private String externIp = "";
        private String playType = "play";
    }

    /**
     * 启动时如果 externIp 为空，自动检测本机局域网 IP
     */
    @PostConstruct
    public void init() {
        String ip = webrtc.getExternIp();
        if (ip == null || ip.trim().isEmpty()) {
            ip = detectLocalIp();
            webrtc.setExternIp(ip);
            log.info("zlm.webrtc.extern-ip auto-detected: {}", ip);
        } else {
            log.info("zlm.webrtc.extern-ip configured: {}", ip);
        }
    }

    /**
     * 自动检测本机非回环 IP，优先取 192.168.x.x / 10.x.x.x 等局域网地址
     */
    private String detectLocalIp() {
        try {
            // 优先通过连接外网的方式获取本机出口 IP
            InetAddress addr = InetAddress.getLocalHost();
            if (!addr.isLoopbackAddress() && !addr.getHostAddress().equals("127.0.0.1")) {
                return addr.getHostAddress();
            }
            // 遍历网卡，找第一个非回环、已启用的 IPv4 地址
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics != null && nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (nic.isLoopback() || !nic.isUp()) continue;
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a.isLoopbackAddress() || !(a instanceof java.net.Inet4Address)) continue;
                    // 优先局域网地址
                    String ipStr = a.getHostAddress();
                    if (ipStr.startsWith("192.168.") || ipStr.startsWith("10.") || ipStr.startsWith("172.")) {
                        return ipStr;
                    }
                }
            }
            // 兜底
            return "127.0.0.1";
        } catch (Exception e) {
            log.warn("Failed to auto-detect local IP, fallback to 127.0.0.1: {}", e.getMessage());
            return "127.0.0.1";
        }
    }
}
