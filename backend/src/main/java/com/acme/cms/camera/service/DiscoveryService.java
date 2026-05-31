package com.acme.cms.camera.service;

import com.acme.cms.config.OnvifProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WS-Discovery 实现 - 通过 UDP 239.255.255.250:3702 发送 Probe 多播
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private static final String DISCOVERY_HOST = "239.255.255.250";
    private static final int DISCOVERY_PORT = 3702;
    private static final Pattern XADDRS_PATTERN =
            Pattern.compile("<[^:>]*:?XAddrs>([^<]+)</[^:>]*:?XAddrs>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPES_PATTERN =
            Pattern.compile("<[^:>]*:?Types>([^<]+)</[^:>]*:?Types>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCOPES_PATTERN =
            Pattern.compile("<[^:>]*:?Scopes[^>]*>([^<]+)</[^:>]*:?Scopes>", Pattern.CASE_INSENSITIVE);

    private final OnvifProperties props;

    public List<DiscoveredDevice> scan(int timeoutMs) throws IOException {
        if (timeoutMs <= 0) timeoutMs = props.getDiscoveryTimeoutMs();

        String messageId = UUID.randomUUID().toString();
        String probe = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\""
                + " xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\""
                + " xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\""
                + " xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">"
                + "<e:Header>"
                + "<w:MessageID>uuid:" + messageId + "</w:MessageID>"
                + "<w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>"
                + "<w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>"
                + "</e:Header>"
                + "<e:Body>"
                + "<d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe>"
                + "</e:Body>"
                + "</e:Envelope>";

        byte[] data = probe.getBytes(StandardCharsets.UTF_8);
        Map<String, DiscoveredDevice> results = new LinkedHashMap<>();

        try (MulticastSocket socket = new MulticastSocket()) {
            socket.setSoTimeout(800);
            socket.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(DISCOVERY_HOST);
            DatagramPacket out = new DatagramPacket(data, data.length, group, DISCOVERY_PORT);

            // 通过每个网卡发送, 避免只走默认路由
            sendOnAllInterfaces(socket, out);

            byte[] buf = new byte[8192];
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    socket.receive(recv);
                    String resp = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                    DiscoveredDevice dev = parse(resp, recv.getAddress().getHostAddress());
                    if (dev != null && dev.getXaddr() != null) {
                        results.putIfAbsent(dev.getXaddr(), dev);
                    }
                } catch (SocketTimeoutException ignore) {
                    // 继续直到 deadline
                }
            }
        }
        return new ArrayList<>(results.values());
    }

    private void sendOnAllInterfaces(MulticastSocket socket, DatagramPacket out) {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            boolean sent = false;
            while (nis != null && nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
                try {
                    socket.setNetworkInterface(ni);
                    socket.send(out);
                    sent = true;
                } catch (Exception ignore) {}
            }
            if (!sent) {
                socket.send(out);  // 默认接口兜底
            }
        } catch (Exception e) {
            log.warn("WS-Discovery send failed: {}", e.getMessage());
        }
    }

    private DiscoveredDevice parse(String resp, String fromIp) {
        Matcher mx = XADDRS_PATTERN.matcher(resp);
        if (!mx.find()) return null;
        String xaddrs = mx.group(1).trim();
        String first = xaddrs.split("\\s+")[0];

        DiscoveredDevice d = new DiscoveredDevice();
        d.setXaddr(first);
        d.setFromIp(fromIp);
        try {
            URI u = new URI(first);
            d.setIp(u.getHost());
            d.setPort(u.getPort() > 0 ? u.getPort() : 80);
        } catch (Exception e) {
            d.setIp(fromIp);
            d.setPort(80);
        }

        Matcher mt = TYPES_PATTERN.matcher(resp);
        if (mt.find()) d.setTypes(mt.group(1).trim());

        Matcher ms = SCOPES_PATTERN.matcher(resp);
        if (ms.find()) {
            String scopes = ms.group(1).trim();
            d.setScopes(scopes);
            for (String s : scopes.split("\\s+")) {
                if (s.contains("name/")) d.setName(decodeScope(s.substring(s.lastIndexOf('/') + 1)));
                else if (s.contains("hardware/")) d.setHardware(decodeScope(s.substring(s.lastIndexOf('/') + 1)));
            }
        }
        return d;
    }

    private static String decodeScope(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    @Data
    public static class DiscoveredDevice {
        private String xaddr;
        private String ip;
        private int port;
        private String fromIp;
        private String types;
        private String scopes;
        private String name;
        private String hardware;
    }
}
