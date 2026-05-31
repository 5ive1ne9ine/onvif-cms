# ONVIF Camera Management System (onvif-cms)

一个基于 Spring Boot 2.7 + Vue 3 的局域网 ONVIF 摄像头管理系统。

## 功能

- 🔍 **WS-Discovery 设备发现** - 一键扫描局域网 ONVIF 摄像头
- 📺 **WebRTC 低延迟实时预览** - 通过 ZLMediaKit 中转, 延迟 <500ms
- 🎮 **PTZ 操控** - 旋转、变焦、预置位
- 🔔 **ONVIF 事件订阅** - 订阅摄像头原生事件 (移动侦测、遮挡告警、IO 输入等)
- 📼 **事件触发录制 + 抓图** - 自定义规则, 触发后保存视频片段和截图
- 🌐 **Web 管理界面** - Vue3 + Element Plus 完整管理 UI
- 🔐 **JWT 认证** - 多用户登录, 默认 admin/admin123

## 技术栈

| 层面 | 技术 |
|---|---|
| 后端 | Spring Boot 2.7.18 + Java 8 + Maven |
| 数据库 | MySQL 8.x + MyBatis-Plus 3.5.5 |
| 流媒体 | ZLMediaKit (RTSP→WebRTC) |
| 事件录制 | FFmpeg (从摄像头拉流录制) |
| 前端 | Vue 3 + Element Plus + Vite + TypeScript |
| ONVIF | 自实现轻量 SOAP 客户端 (WS-Security Digest) |
| 认证 | JWT (jjwt 0.11.5) + BCrypt |

## 项目结构

```
onvif-cms/
├── pom.xml                  父 Maven POM
├── sql/schema.sql           数据库初始化脚本
├── docker/                  Docker 部署 (compose + ZLM 配置 + Dockerfile)
├── backend/                 Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/acme/cms/
│       ├── CmsApplication.java
│       ├── auth/            JWT 认证
│       ├── camera/          摄像头管理 + 发现 + PTZ + ONVIF 客户端
│       ├── stream/          ZLMediaKit 集成 + WebRTC 信令
│       ├── event/           事件规则 + ONVIF PullPoint 订阅 + 分发
│       ├── record/          录制管理 + 事件录制编排
│       ├── user/            用户
│       ├── common/          通用 (R, 异常, 工具)
│       └── config/          配置
└── frontend/                Vue 3 SPA
    ├── package.json
    ├── vite.config.ts       开发代理 + 构建输出到 backend/static
    └── src/
        ├── api/             axios 封装
        ├── components/      WebRtcPlayer / PtzPanel
        ├── layouts/         MainLayout
        ├── stores/          Pinia (auth)
        └── views/           login / dashboard / camera / preview / event / recording
```

## 快速开始 (开发模式)

### 1. 启动依赖服务 (MySQL + ZLMediaKit)

需要先准备好 MySQL 8.x 和 [ZLMediaKit](https://github.com/ZLMediaKit/ZLMediaKit) (建议使用 Docker)。

```bash
cd docker
docker compose up -d mysql zlm
```

或者自行启动 MySQL 与 ZLM, 然后:

```bash
# 导入数据库
mysql -uroot -proot < sql/schema.sql
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器: `http://localhost:5173` (API 已配置代理到 `:8080`)。

### 4. 登录

打开 `http://localhost:5173`, 使用默认账号 `admin / admin123` 登录。

## 生产部署

### 方式 A: Docker Compose 一键部署 (推荐)

```bash
# 在项目根目录
cd docker
docker compose up -d --build
```

启动后访问 `http://<host>:8080`。

### 方式 B: 打包部署

```bash
# 1. 构建前端 (输出会写入 backend/src/main/resources/static)
cd frontend && npm install && npm run build

# 2. 打包后端
cd ../backend && mvn clean package -DskipTests
# 产物: backend/target/onvif-cms.jar

# 3. 运行
java -jar backend/target/onvif-cms.jar --spring.profiles.active=prod
```

## 配置说明

主要配置位于 `backend/src/main/resources/application.yml`:

| Key | 说明 |
|---|---|
| `spring.datasource.*` | MySQL 连接 |
| `cms.jwt.secret` | JWT 签名密钥, 至少 32 字节 |
| `cms.jwt.expire-minutes` | Token 有效期 (默认 720 分钟) |
| `cms.onvif.aes-key` | 摄像头密码加密 Key (16 字节) |
| `cms.storage.record-dir` | 录像文件存储目录 |
| `cms.storage.snapshot-dir` | 截图存储目录 |
| `cms.ffmpeg.bin` | FFmpeg 可执行文件路径 (默认假定在 PATH) |
| `zlm.base-url` | ZLMediaKit API 地址, 默认 `http://localhost:80` |
| `zlm.secret` | ZLM API secret, 必须与 ZLM `config.ini` 一致 |
| `zlm.webrtc.extern-ip` | WebRTC ICE 候选地址, 应为客户端可访问的 IP |

生产环境通过环境变量覆盖, 见 `application-prod.yml`。

## 端口清单

| 端口 | 用途 |
|---|---|
| 8080 | Spring Boot HTTP (API + 前端) |
| 80   | ZLMediaKit HTTP API + WebRTC 信令 |
| 8000/UDP+TCP | ZLMediaKit WebRTC 媒体 |
| 554  | ZLMediaKit RTSP (可选) |
| 3306 | MySQL |
| 3702/UDP (multicast 239.255.255.250) | WS-Discovery |

## 注意事项

1. **时间同步**: 大多数 ONVIF 摄像头要求 SOAP 请求的时间戳与摄像头时间差 <5s。建议为部署主机配置 NTP。
2. **网络模式**: ZLMediaKit Docker 容器使用 host 网络模式, 避免 WebRTC 与 RTSP 的 NAT 穿透复杂性。
3. **FFmpeg**: 事件触发录制依赖 FFmpeg, 请确保已安装 (`ffmpeg --version`)。Docker 镜像已内置。
4. **摄像头密码**: 持久化时使用 AES-128 加密, Key 由 `cms.onvif.aes-key` 控制 (生产请改为随机值)。
5. **WS-Discovery**: 多播包只在二层网络内可达, 跨网段需手动添加摄像头。
6. **WebRTC IP**: `zlm.webrtc.extern-ip` 必须是客户端浏览器能访问的 IP, 否则 ICE 失败。

## API 接口

完整列表见代码, 主要端点:

- `POST /api/auth/login` 登录
- `GET /api/cameras` 摄像头列表
- `POST /api/cameras` 新增摄像头 (自动探测能力)
- `POST /api/discovery/scan` WS-Discovery 扫描
- `POST /api/cameras/{id}/ptz/continuous` PTZ 持续移动
- `POST /api/stream/{id}/start` 启动 WebRTC 流
- `POST /api/stream/{id}/webrtc/offer` WebRTC 信令
- `GET /api/events` 事件日志
- `POST /api/event-rules` 新增事件规则
- `GET /api/recordings` 录像列表
- `GET /api/recordings/{id}/play` 录像播放 (支持 Range)

## License

MIT
