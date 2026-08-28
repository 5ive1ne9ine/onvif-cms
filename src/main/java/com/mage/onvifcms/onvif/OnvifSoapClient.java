package com.mage.onvifcms.onvif;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class OnvifSoapClient {
    private static final String DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl";
    private static final String MEDIA_NS = "http://www.onvif.org/ver10/media/wsdl";
    private static final String PTZ_NS = "http://www.onvif.org/ver20/ptz/wsdl";

    public DeviceInformation getDeviceInformation(String serviceUrl, Credentials credentials) {
        Document document = call(serviceUrl, DEVICE_NS + "/GetDeviceInformation", credentials,
                "<tds:GetDeviceInformation/>");
        return new DeviceInformation(
                XmlSupport.firstText(document, "Manufacturer"),
                XmlSupport.firstText(document, "Model"),
                XmlSupport.firstText(document, "FirmwareVersion"),
                XmlSupport.firstText(document, "SerialNumber"),
                XmlSupport.firstText(document, "HardwareId"));
    }

    public Capabilities getCapabilities(String serviceUrl, Credentials credentials) {
        Document document = call(serviceUrl, DEVICE_NS + "/GetCapabilities", credentials,
                "<tds:GetCapabilities><tds:Category>All</tds:Category></tds:GetCapabilities>");
        return new Capabilities(capabilityXAddr(document, "Media"), capabilityXAddr(document, "PTZ"));
    }

    public MediaProfile getPrimaryProfile(String mediaServiceUrl, Credentials credentials) {
        Document document = call(mediaServiceUrl, MEDIA_NS + "/GetProfiles", credentials, "<trt:GetProfiles/>");
        List<Element> profiles = XmlSupport.elements(document, "Profiles");
        if (profiles.isEmpty()) throw new OnvifException("摄像头没有返回可用的媒体 Profile");

        Element selected = profiles.stream()
                .filter(profile -> profile.getElementsByTagNameNS("*", "VideoEncoderConfiguration").getLength() > 0)
                .findFirst().orElse(profiles.get(0));
        String token = selected.getAttribute("token");
        if (token == null || token.isBlank()) throw new OnvifException("摄像头媒体 Profile 缺少 token");
        String name = XmlSupport.firstDescendantText(selected, "Name");
        boolean hasPtzConfiguration = selected.getElementsByTagNameNS("*", "PTZConfiguration").getLength() > 0;
        return new MediaProfile(token, name, hasPtzConfiguration);
    }

    public String getStreamUri(String mediaServiceUrl, String profileToken, Credentials credentials) {
        String body = """
                <trt:GetStreamUri>
                  <trt:StreamSetup>
                    <tt:Stream>RTP-Unicast</tt:Stream>
                    <tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport>
                  </trt:StreamSetup>
                  <trt:ProfileToken>%s</trt:ProfileToken>
                </trt:GetStreamUri>
                """.formatted(XmlSupport.escape(profileToken));
        Document document = call(mediaServiceUrl, MEDIA_NS + "/GetStreamUri", credentials, body);
        String uri = XmlSupport.firstText(document, "Uri");
        if (uri == null || uri.isBlank()) throw new OnvifException("摄像头没有返回 RTSP 地址");
        return uri;
    }

    public void continuousMove(String ptzServiceUrl, String profileToken, Credentials credentials,
                               double pan, double tilt, double zoom) {
        String body = """
                <tptz:ContinuousMove>
                  <tptz:ProfileToken>%s</tptz:ProfileToken>
                  <tptz:Velocity>
                    <tt:PanTilt x="%s" y="%s"/>
                    <tt:Zoom x="%s"/>
                  </tptz:Velocity>
                </tptz:ContinuousMove>
                """.formatted(XmlSupport.escape(profileToken), decimal(pan), decimal(tilt), decimal(zoom));
        call(ptzServiceUrl, PTZ_NS + "/ContinuousMove", credentials, body);
    }

    public void stop(String ptzServiceUrl, String profileToken, Credentials credentials) {
        String body = """
                <tptz:Stop>
                  <tptz:ProfileToken>%s</tptz:ProfileToken>
                  <tptz:PanTilt>true</tptz:PanTilt>
                  <tptz:Zoom>true</tptz:Zoom>
                </tptz:Stop>
                """.formatted(XmlSupport.escape(profileToken));
        call(ptzServiceUrl, PTZ_NS + "/Stop", credentials, body);
    }

    private Document call(String serviceUrl, String action, Credentials credentials, String body) {
        try {
            String envelope = envelope(credentials, body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(serviceUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"" + action + "\"")
                    .header("SOAPAction", "\"" + action + "\"")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();
            HttpClient client = httpClient(credentials);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String fault = soapFault(response.body());
                throw new OnvifException("ONVIF 请求失败（HTTP " + response.statusCode() + ")" +
                        (fault == null ? "" : "：" + fault));
            }
            Document document = XmlSupport.parse(response.body());
            String fault = XmlSupport.firstText(document, "Text");
            if (!XmlSupport.elements(document, "Fault").isEmpty()) {
                throw new OnvifException("摄像头返回 SOAP Fault" + (fault == null ? "" : "：" + fault));
            }
            return document;
        } catch (OnvifException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new OnvifException("无法连接 ONVIF 服务 " + serviceUrl + "：" + exception.getMessage(), exception);
        }
    }

    private HttpClient httpClient(Credentials credentials) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (credentials != null && credentials.username() != null && !credentials.username().isBlank()) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(credentials.username(), credentials.password().toCharArray());
                }
            });
        }
        return builder.build();
    }

    private String envelope(Credentials credentials, String body) {
        String security = credentials == null ? "" : WsSecurity.header(credentials.username(), credentials.password());
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
                    xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
                    xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl"
                    xmlns:tt="http://www.onvif.org/ver10/schema">
                  <s:Header>%s</s:Header>
                  <s:Body>%s</s:Body>
                </s:Envelope>
                """.formatted(security, body).stripLeading();
    }

    private String capabilityXAddr(Document document, String capabilityName) {
        for (Element element : XmlSupport.elements(document, capabilityName)) {
            String xaddr = XmlSupport.firstDescendantText(element, "XAddr");
            if (xaddr != null && !xaddr.isBlank()) return xaddr;
        }
        return null;
    }

    private String soapFault(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            Document document = XmlSupport.parse(response);
            return XmlSupport.firstText(document, "Text");
        } catch (Exception ignored) {
            return null;
        }
    }

    private String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(-1.0, Math.min(1.0, value)));
    }

    public record Credentials(String username, String password) {
        public Credentials {
            username = username == null ? "" : username;
            password = password == null ? "" : password;
        }
    }

    public record DeviceInformation(String manufacturer, String model, String firmwareVersion,
                                    String serialNumber, String hardwareId) {}

    public record Capabilities(String mediaServiceUrl, String ptzServiceUrl) {}

    public record MediaProfile(String token, String name, boolean hasPtzConfiguration) {}
}
