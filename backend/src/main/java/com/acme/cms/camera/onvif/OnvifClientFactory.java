package com.acme.cms.camera.onvif;

import com.acme.cms.camera.entity.Camera;
import com.acme.cms.common.util.AesUtil;
import com.acme.cms.config.OnvifProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * ONVIF 客户端工厂 - 提供构造 SOAP 客户端、查询设备能力、媒体 profile 等的统一入口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnvifClientFactory {

    private final OnvifProperties onvifProps;

    public String deviceServiceUrl(String ip, int port) {
        return "http://" + ip + ":" + port + "/onvif/device_service";
    }

    public OnvifSoapClient deviceClient(Camera cam) {
        String pwd = AesUtil.decrypt(cam.getPassword(), onvifProps.getAesKey());
        return new OnvifSoapClient(deviceServiceUrl(cam.getIp(), cam.getOnvifPort()),
                cam.getUsername(), pwd);
    }

    public OnvifSoapClient client(Camera cam, String xaddr) {
        String pwd = AesUtil.decrypt(cam.getPassword(), onvifProps.getAesKey());
        return new OnvifSoapClient(xaddr, cam.getUsername(), pwd);
    }

    /**
     * 探测设备完整信息: GetDeviceInformation + GetCapabilities + GetProfiles + GetStreamUri
     */
    public OnvifDeviceInfo probe(Camera cam) throws Exception {
        OnvifDeviceInfo info = new OnvifDeviceInfo();
        OnvifSoapClient device = deviceClient(cam);

        // 1) GetDeviceInformation
        try {
            String resp = device.call(
                    "http://www.onvif.org/ver10/device/wsdl/GetDeviceInformation",
                    "<tds:GetDeviceInformation/>", true);
            Document d = OnvifSoapClient.parseXml(resp);
            info.setManufacturer(text(d, "Manufacturer"));
            info.setModel(text(d, "Model"));
            info.setFirmware(text(d, "FirmwareVersion"));
            info.setSerialNumber(text(d, "SerialNumber"));
            info.setHardwareId(text(d, "HardwareId"));
        } catch (Exception e) {
            log.warn("GetDeviceInformation failed for {}: {}", cam.getIp(), e.getMessage());
        }

        // 2) GetCapabilities (All)
        String capsResp = device.call(
                "http://www.onvif.org/ver10/device/wsdl/GetCapabilities",
                "<tds:GetCapabilities><tds:Category>All</tds:Category></tds:GetCapabilities>", true);
        Document caps = OnvifSoapClient.parseXml(capsResp);
        info.setMediaXAddr(findXAddr(caps, "Media"));
        info.setPtzXAddr(findXAddr(caps, "PTZ"));
        info.setEventsXAddr(findXAddr(caps, "Events"));
        info.setImagingXAddr(findXAddr(caps, "Imaging"));
        info.setPtzSupported(info.getPtzXAddr() != null);
        info.setEventsSupported(info.getEventsXAddr() != null);

        // 3) GetProfiles
        List<OnvifDeviceInfo.Profile> profiles = new ArrayList<>();
        if (info.getMediaXAddr() != null) {
            OnvifSoapClient media = client(cam, sanitizeXaddr(cam.getIp(), info.getMediaXAddr()));
            String profResp = media.call(
                    "http://www.onvif.org/ver10/media/wsdl/GetProfiles",
                    "<trt:GetProfiles/>", true);
            Document pd = OnvifSoapClient.parseXml(profResp);
            NodeList nl = pd.getElementsByTagNameNS("*", "Profiles");
            for (int i = 0; i < nl.getLength(); i++) {
                Element prof = (Element) nl.item(i);
                String token = prof.getAttribute("token");
                String name = textOf(prof, "Name");

                OnvifDeviceInfo.Profile p = new OnvifDeviceInfo.Profile();
                p.setToken(token);
                p.setName(name);

                // GetStreamUri
                try {
                    String streamResp = media.call(
                            "http://www.onvif.org/ver10/media/wsdl/GetStreamUri",
                            "<trt:GetStreamUri>"
                                    + "<trt:StreamSetup>"
                                    + "<tt:Stream>RTP-Unicast</tt:Stream>"
                                    + "<tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport>"
                                    + "</trt:StreamSetup>"
                                    + "<trt:ProfileToken>" + token + "</trt:ProfileToken>"
                                    + "</trt:GetStreamUri>", true);
                    Document sd = OnvifSoapClient.parseXml(streamResp);
                    p.setRtspUrl(text(sd, "Uri"));
                } catch (Exception ex) {
                    log.warn("GetStreamUri failed for profile {}: {}", token, ex.getMessage());
                }

                // GetSnapshotUri
                try {
                    String snapResp = media.call(
                            "http://www.onvif.org/ver10/media/wsdl/GetSnapshotUri",
                            "<trt:GetSnapshotUri><trt:ProfileToken>" + token
                                    + "</trt:ProfileToken></trt:GetSnapshotUri>", true);
                    Document sd = OnvifSoapClient.parseXml(snapResp);
                    p.setSnapshotUrl(text(sd, "Uri"));
                } catch (Exception ignore) {}

                profiles.add(p);
            }
        }
        info.setProfiles(profiles);
        return info;
    }

    // -------- helpers --------

    private static String text(Document d, String localName) {
        NodeList nl = d.getElementsByTagNameNS("*", localName);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent();
    }

    private static String textOf(Element parent, String localName) {
        NodeList nl = parent.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getParentNode() == parent) {
                return n.getTextContent();
            }
        }
        if (nl.getLength() > 0) return nl.item(0).getTextContent();
        return null;
    }

    private static String findXAddr(Document d, String serviceLocalName) {
        NodeList nl = d.getElementsByTagNameNS("*", serviceLocalName);
        for (int i = 0; i < nl.getLength(); i++) {
            Element el = (Element) nl.item(i);
            NodeList xa = el.getElementsByTagNameNS("*", "XAddr");
            if (xa.getLength() > 0) {
                return xa.item(0).getTextContent();
            }
        }
        return null;
    }

    /**
     * 摄像头返回的 XAddr 可能是内部地址 (如 192.168.x.x), 但如果用户给的 IP 已经能访问
     * 则使用用户 IP 替换 host. 端口和路径保留.
     */
    public static String sanitizeXaddr(String userIp, String xaddr) {
        try {
            URI u = new URI(xaddr);
            int port = u.getPort();
            return u.getScheme() + "://" + userIp + (port > 0 ? ":" + port : "") + u.getPath();
        } catch (Exception e) {
            return xaddr;
        }
    }
}
