package com.mage.onvifcms.onvif;

import com.mage.onvifcms.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WsDiscoveryClient {
    private static final Logger log = LoggerFactory.getLogger(WsDiscoveryClient.class);
    private static final InetSocketAddress TARGET = new InetSocketAddress("239.255.255.250", 3702);
    private final AppProperties properties;

    public WsDiscoveryClient(AppProperties properties) {
        this.properties = properties;
    }

    public List<DiscoveredDevice> discover() {
        Map<String, DiscoveredDevice> devices = new LinkedHashMap<>();
        byte[] probe = probeMessage().getBytes(StandardCharsets.UTF_8);
        for (InetAddress address : localIpv4Addresses()) {
            discoverOnInterface(address, probe, devices);
        }
        if (devices.isEmpty()) discoverOnInterface(null, probe, devices);
        return List.copyOf(devices.values());
    }

    private void discoverOnInterface(InetAddress localAddress, byte[] probe,
                                     Map<String, DiscoveredDevice> devices) {
        try (DatagramSocket socket = localAddress == null
                ? new DatagramSocket()
                : new DatagramSocket(new InetSocketAddress(localAddress, 0))) {
            socket.setSoTimeout(250);
            socket.send(new DatagramPacket(probe, probe.length, TARGET));
            Instant deadline = Instant.now().plusMillis(properties.discovery().timeoutMillis());
            byte[] buffer = new byte[64 * 1024];
            while (Instant.now().isBefore(deadline)) {
                try {
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    String xml = new String(response.getData(), response.getOffset(), response.getLength(), StandardCharsets.UTF_8);
                    for (DiscoveredDevice device : parseResponse(xml)) devices.put(device.stableKey(), device);
                } catch (java.net.SocketTimeoutException ignored) {
                    // Poll until the full discovery deadline so slower cameras still appear.
                }
            }
        } catch (Exception exception) {
            log.debug("WS-Discovery failed on interface {}: {}", localAddress, exception.getMessage());
        }
    }

    private List<InetAddress> localIpv4Addresses() {
        try {
            List<InetAddress> addresses = new ArrayList<>();
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual() || !network.supportsMulticast()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) addresses.add(address);
                }
            }
            return addresses;
        } catch (Exception exception) {
            log.warn("无法枚举局域网网卡，将使用系统默认网卡", exception);
            return List.of();
        }
    }

    static List<DiscoveredDevice> parseResponse(String xml) {
        Document document = XmlSupport.parse(xml);
        List<DiscoveredDevice> devices = new ArrayList<>();
        for (Element match : XmlSupport.elements(document, "ProbeMatch")) {
            String endpoint = XmlSupport.firstDescendantText(match, "Address");
            String xaddrs = XmlSupport.firstDescendantText(match, "XAddrs");
            if (xaddrs == null || xaddrs.isBlank()) continue;
            List<String> scopes = split(XmlSupport.firstDescendantText(match, "Scopes"));
            List<String> types = split(XmlSupport.firstDescendantText(match, "Types"));
            for (String xaddr : split(xaddrs)) {
                try {
                    URI uri = URI.create(xaddr);
                    if (uri.getHost() != null) {
                        devices.add(new DiscoveredDevice(endpoint, xaddr, uri.getHost(), scopes, types));
                        break;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed addresses while keeping other ProbeMatches.
                }
            }
        }
        return devices;
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
    }

    private String probeMessage() {
        String messageId = "uuid:" + UUID.randomUUID();
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:a="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                    xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
                    xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                  <s:Header>
                    <a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>
                    <a:MessageID>%s</a:MessageID>
                    <a:ReplyTo><a:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</a:Address></a:ReplyTo>
                    <a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To>
                  </s:Header>
                  <s:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></s:Body>
                </s:Envelope>
                """.formatted(messageId).stripLeading();
    }
}

