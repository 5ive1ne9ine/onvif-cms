package com.acme.cms.camera.onvif;

import lombok.extern.slf4j.Slf4j;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * 极简 ONVIF SOAP 客户端 - 使用 HttpURLConnection 直接发送 SOAP 1.2 请求
 */
@Slf4j
public class OnvifSoapClient {

    private static final String SOAP_ENV_NS = "http://www.w3.org/2003/05/soap-envelope";

    private final String url;
    private final String username;
    private final String password;
    private final int timeoutMs;

    public OnvifSoapClient(String url, String username, String password) {
        this(url, username, password, 8000);
    }

    public OnvifSoapClient(String url, String username, String password, int timeoutMs) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 发送 SOAP 请求, 返回响应 XML 字符串
     *
     * @param actionUri    SOAP Action URI (可空)
     * @param bodyXml      <soap:Body> 内部的 XML (不包含 Body 标签本身)
     * @param requireAuth  是否需要在头部包含 WS-Security UsernameToken
     */
    public String call(String actionUri, String bodyXml, boolean requireAuth) throws Exception {
        String envelope = buildEnvelope(actionUri, bodyXml, requireAuth);
        byte[] payload = envelope.getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8"
                    + (actionUri != null ? "; action=\"" + actionUri + "\"" : ""));
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String resp = readAll(is);
            if (code >= 400) {
                throw new RuntimeException("ONVIF SOAP HTTP " + code + ": " + resp);
            }
            return resp;
        } finally {
            conn.disconnect();
        }
    }

    private String buildEnvelope(String actionUri, String bodyXml, boolean requireAuth) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<soap:Envelope xmlns:soap=\"").append(SOAP_ENV_NS).append("\"")
                .append(" xmlns:tds=\"http://www.onvif.org/ver10/device/wsdl\"")
                .append(" xmlns:trt=\"http://www.onvif.org/ver10/media/wsdl\"")
                .append(" xmlns:tev=\"http://www.onvif.org/ver10/events/wsdl\"")
                .append(" xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\"")
                .append(" xmlns:tt=\"http://www.onvif.org/ver10/schema\"")
                .append(" xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">");
        sb.append("<soap:Header>");
        if (actionUri != null) {
            sb.append("<wsa:Action>").append(actionUri).append("</wsa:Action>");
        }
        if (requireAuth && username != null && !username.isEmpty()) {
            sb.append(WsSecurityHeader.build(username, password));
        }
        sb.append("</soap:Header>");
        sb.append("<soap:Body>").append(bodyXml).append("</soap:Body>");
        sb.append("</soap:Envelope>");
        return sb.toString();
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new InputSource(new java.io.StringReader(xml)));
    }
}
