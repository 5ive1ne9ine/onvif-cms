# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

局域网 ONVIF 摄像头管理系统:WS-Discovery 设备发现、WebRTC 实时预览(经 ZLMediaKit 中转)、PTZ 操控、ONVIF 事件订阅、事件触发录制/抓图、JWT 认证。Spring Boot 2.7.18 + Java 8 + Vue 3。

**技术栈:** Java 8(`javax.*`,非 `jakarta.*`)· Spring Boot 2.7.18 · MyBatis-Plus 3.5.5 · MySQL 8 · jjwt 0.11.5 · ZLMediaKit · FFmpeg · Vue 3 + Element Plus + Vite 5 + TypeScript。

> 注:本仓库早期有过 Spring Boot 3 / Java 17 / jakarta 分支(含 AI/HA 集成),已废弃。当前 `dev`/`main` 为 Spring Boot 2.7 + Java 8。不要把 Dockerfile "升级" 到 JDK 17。

## 构建与运行

### 后端(Maven,JDK 8)
机器默认 `mvn` 绑定 JDK 8(`D:\3.Tool\Java\jdk\jdk1.8.0_231`)。Maven 3.6.3 在 `D:\3.Tool\Java\maven\maven-3.6.3`。
```bash
# 开发构建(在仓库根或 backend/ 下)
mvn -B clean package -DskipTests            # 产物: backend/target/onvif-cms.jar
mvn -f backend/pom.xml spring-boot:run      # 开发运行(端口 18080)
```

**关键:** `application.yml` 被打包进 jar 的 `BOOT-INF/classes/`。改配置后必须重新 `mvn package` —— 跑旧 jar 用的还是旧值。运行时也可用 `--spring.datasource.password=...` 等参数覆盖。

运行打包好的 jar:
```bash
java -jar backend/target/onvif-cms.jar [--spring.profiles.active=prod]
```
Windows 上 `mvn clean` 前要先 `taskkill` 掉运行中的 java 进程 —— jar 文件锁会导致删除失败。

### 前端(Vite)
```bash
cd frontend
npm install
npm run dev      # 开发服务器 :5173, /api 与 /zlm 代理到 :18080
npm run build    # vue-tsc + vite build → 输出到 backend/src/main/resources/static(打进 JAR)
```

### 数据库
单一初始化脚本 `sql/schema.sql`(5 张表 + admin 种子)。无 Flyway/Liquibase。默认账号:`admin` / `admin123`。
```bash
docker run --rm -v "E:/1.Code/onvif-cms/sql:/sql:ro" mysql:8.0 \
  mysql -h<DB_HOST> -P<DB_PORT> -uroot -p<pass> onvif_cms -e "source /sql/schema.sql;"
```

### 测试
没有测试套件。验证靠冒烟测试(登录 → 调一个鉴权接口)。`R<T>` 包装统一返回 HTTP 200,业务状态在 body 的 `code` 字段(`0`=成功,`401`=未登录)。

### Docker —— 两套 compose 文件
- `docker/docker-compose.yml` —— 原 3 服务栈(mysql + zlm + cms)。
- `docker/docker-compose.app.yml` —— **聚合部署**(仅 zlm + cms,数据库用外部 MySQL)。所有对外端口落在 20000–29999,避开宿主端口冲突。CMS 容器通过 `docker exec onvif-cms-zlm ffmpeg ...` 转码,因此 CMS 容器挂载了 `/var/run/docker.sock`(等价宿主 root 权限 —— 仅在需要主码流转码时开启)。ZLM 的 `externIP` 在 `zlm-config.app.ini` 中以 `__HOST_LAN_IP__` 占位,启动时用 `ZLM_EXTERN_IP` 替换。
```bash
cp docker/.env.app.example docker/.env.app     # 填 ZLM_EXTERN_IP(宿主 LAN IP,浏览器需可达)
docker compose -f docker/docker-compose.app.yml --env-file docker/.env.app up -d --build
```

## 端口清单(开发)

| 端口 | 服务 |
|---|---|
| 18080 | Spring Boot(开发应用端口;生产 compose 把宿主 28080 映射到 18080) |
| 5173 | Vite 开发服务器 |
| 80 / 20080 | ZLM HTTP API(宿主端口取决于用哪套 compose) |
| 8000, 10000, 3478 | ZLM WebRTC/TURN 媒体(UDP+TCP) |
| 3306 / 23306 | MySQL |
| 3702/UDP(多播 239.255.255.250) | WS-Discovery |

## 架构

Maven 多模块(`onvif-cms-parent` → `backend`)。前端构建产物落入后端 JAR 的 `static/`,形成单一可部署产物。

### 后端包结构(`com.acme.cms`)
- **`auth/`** —— 自实现 JWT(无 Spring Security)。`JwtAuthFilter`(OncePerRequestFilter)拦截 `/api/**` 和 `/zlm/**`,放行 `/api/auth/login` + `/zlm/hook/**`,校验 Bearer JWT,设置 `UserContext`(ThreadLocal)。`JwtUtil` 用 HMAC-SHA256(720min)。`AuthService` 用 BCrypt 校验(仅引入 `spring-security-crypto`,不用完整框架)。
- **`camera/`** —— Camera 实体/mapper/service/controller,**自实现 ONVIF SOAP 客户端**(`OnvifSoapClient` 裸 HttpURLConnection + WS-Security UsernameToken digest;`OnvifClientFactory` 探测 GetDeviceInformation/GetCapabilities/GetProfiles/GetStreamUri;`WsSecurityHeader`)。`DiscoveryService` 在所有网卡上向 239.255.255.250:3702 发 UDP 多播 WS-Discovery Probe。`PtzService` ContinuousMove/Stop/Relative/Absolute/Preset。
- **`stream/`** —— ZLMediaKit 集成。`ZlmClient`(HTTP API:addStreamProxy、delStreamProxy、isStreamAlive、startRecord/stopRecord、getSnap、webrtcSignal)。`StreamService.ensureProxy()` 按摄像头管理 RTSP 代理生命周期:先尝试 H265→H264 ffmpeg 转码(通过 `docker exec onvif-cms-zlm ffmpeg`),失败回退子码流直拉,轮询直到流存活(20s 超时)。`ZlmHookController` 接收 ZLM webhook(on_publish、on_stream_changed、on_record_mp4 等)。
- **`event/`** —— `PullPointSubscriptionManager`(每摄像头 PullPoint 订阅,5s 拉取间隔,TTL-15s 自动续订,失败自动重建;启动时刷新所有带规则的摄像头)。`EventDispatcher` 解析 NotificationMessage XML,匹配 `EventRule`,落 `EventLog`,触发 `EventRecordingOrchestrator`。
- **`record/`** —— `RecordingService`(FFmpeg `-i rtsp -t <dur> -c copy` 片段录制,手动录制用 Process 跟踪)。`EventRecordingOrchestrator` 触发时:立即经 ZLM getSnap 抓图 → 录制 (pre+post) 秒片段。
- **`common/`** —— `R<T>` 统一返回、`BizException`、`GlobalExceptionHandler`、`PageResp`、`AesUtil`(AES-128 ECB,加密摄像头密码)。
- **`config/`** —— `MybatisPlusConfig`(分页)、`WebConfig`(CORS 全放行、RestTemplate 5s/15s、SPA 转发到 index.html)、`TaskExecutorConfig`(专用 `onvifScheduler` ThreadPoolTaskScheduler 池=8,CallerRunsPolicy)、各 `*Properties`。

### 前端(`frontend/src`)
Pinia(`stores/auth.ts` —— token/user/role,localStorage 持久化)+ vue-router(history 模式,鉴权守卫,所有路由懒加载)。`api/http.ts` axios 实例,按域拆 api 模块。`components/WebRtcPlayer.vue` 创建 RTCPeerConnection(无 ICE 服务器,本地),等待 ICE 收集完成(最长 2s),把 SDP offer 发到 `/api/stream/{id}/webrtc/offer`,然后**剥离 answer 中非 TCP 的 UDP 候选**强制走 TCP-ICE(Docker Desktop vpnkit 绕过方案)。`components/PtzPanel.vue` 3×3 方向网格 + 变焦,mousedown/up 触发持续移动。

### 关键数据流
- **实时预览:** `LivePreviewView` → `WebRtcPlayer` → `streamApi.start(id)` → 后端 `StreamService.ensureProxy()`(转码或子码流)→ 前端发 SDP offer → 后端转发到 ZLM `/index/api/webrtc` → answer → 视频播放。
- **事件→录制:** PullPoint 每 5s 拉取 → `EventDispatcher` 匹配规则 → 存 `EventLog` → `EventRecordingOrchestrator` 调度抓图(立即)+ 录制片段(pre+post 秒)→ FFmpeg 录制 → 更新 `recording` 表。

## 数据库表
`camera`(RTSP URL、AES 加密密码、状态、PTZ/事件标志)· `event_rule`(按摄像头的 topic、录制/抓图标志、pre/post 秒数)· `event_log`(topic、payload_json、recording_id、snapshot_path)· `recording`(类型 EVENT/MANUAL/SCHEDULED、file_path、duration、status)· `system_user`(BCrypt 密码、role)。

## 关键设计决策与坑

- **无 Spring Security** —— 自实现 JWT 过滤器 + BCrypt。
- **无 XML MyBatis mapper** —— `BaseMapper` + 代码式 `QueryWrapper`(mapper-locations 仍配置着)。
- **摄像头密码** 用 AES-128 ECB 加密存储(`cms.onvif.aes-key`,16 字节)。前端密码字段显示 `******` 占位符;后端仅在提交值不同于占位符时才重新加密。
- **H265 → H264 转码** 用于 WebRTC(浏览器不支持 H265)。由 `docker exec onvif-cms-zlm ffmpeg` 拉主码流、libx264 转码、推回 ZLM 同名流 `cam_{id}`。目标高度/码率由 `CMS_TRANSCODE_HEIGHT`(0=原画质)/ `CMS_TRANSCODE_BITRATE`(0=不限)控制。失败回退子码流(通常 H264 640×480)。此路径需要给 CMS 容器挂载 docker socket。
- **"预录制"并非真正预录** —— 实际是事件后录制,总时长里包含 `pre_seconds`。
- **`AuthController.me()`** 自行从请求头解析 token,而非用 `UserContext`(过滤器把 `/api/auth/**` 当公开路径跳过,所以那里 `UserContext` 是 null)—— 不要用 `Map.of()`(null 值会 NPE)。

## ZLMediaKit 运维要点(Windows Docker Desktop)

- **ZLM secret 三处必须一致:** `application.yml`(`zlm.secret`)、挂载的 `zlm-config*.ini`(`[api] secret`)、运行中的 jar。当前值:`onvifCms2024SecretKey4ZlmApi`。ZLM 仅当 secret 等于内置默认值 `035c73f7-bb6b-4889-a715-d9eb2d1925cc` 时才随机化;任意非默认值都会保留 —— 但前提是 config.ini 的 bind-mount 真正生效。
- **Git Bash 的 MSYS 路径转换会破坏 `docker -v` 的容器侧路径**(把 `/opt/media/...` 转成乱码 Windows 路径 → ZLM 读默认配置 → secret 每次重启都随机化)。修复:`docker` 命令前加 `MSYS_NO_PATHCONV=1`,宿主侧用绝对 Windows 路径。用 `docker inspect ... .Mounts` 确认 `Destination` 是正确的 Unix 路径。**不要**再叠加 `MSYS2_ARG_CONV_EXCL='*'`(它会把宿主侧 `$(pwd)` 展开也禁掉)。
- **Docker Desktop/WSL2 上不能用 host 网络模式** —— 用 bridge + 显式端口映射。WebRTC 媒体需要 UDP:`8000/udp`、`10000/udp`、`3478/udp` 都要映射。
- **WebRTC 黑屏:** 若 `getMediaList` 有 `bytesSpeed > 0` 但视频黑屏,说明流是 H265(见上面转码)。若与 ICE 相关,确保 ZLM `[rtc] externIP` 设为宿主 LAN IP(独立于后端 `zlm.webrtc.extern-ip`),且 UDP 端口已映射。
- 验证 ZLM 拉流是否真活着:`getMediaList` 中 `bytesSpeed > 0` 且 `originSock.identifier = RtspPlayerImp`。不要用 `sh -c 'echo > /dev/tcp/...'` 探测可达性 —— ZLM 容器是 busybox ash(无 `/dev/tcp`);用 `curl --connect-timeout`。

## 配置参考

开发配置在 `backend/src/main/resources/application.yml`;生产用环境变量覆盖,见 `application-prod.yml`。关键 `cms.*`:`jwt.secret`(≥32 字节)、`jwt.expire-minutes`(720)、`storage.record-dir` / `snapshot-dir`、`onvif.aes-key`(16 字节)、`onvif.pull-interval-seconds`(5)、`onvif.subscription-ttl-seconds`(60)、`ffmpeg.bin`。关键 `zlm.*`:`base-url`、`secret`、`default-app`(live)、`rtp-type`、`webrtc.extern-ip`(空=自动探测)、`webrtc.play-type`。

Docker 环境变量(生产):`DB_HOST/PORT/NAME/USER/PASSWORD`、`JWT_SECRET`、`RECORD_DIR`、`SNAPSHOT_DIR`、`ONVIF_AES_KEY`、`ZLM_BASE_URL`、`ZLM_SECRET`、`ZLM_EXTERN_IP`、`ZLM_CONTAINER`(默认 `onvif-cms-zlm`,转码目标容器)、`CMS_TRANSCODE_HEIGHT`、`CMS_TRANSCODE_BITRATE`。

## 时间同步
大多数 ONVIF 摄像头会拒绝时间戳与摄像头时钟相差 >5s 的 SOAP 请求。宿主机建议配置 NTP。
