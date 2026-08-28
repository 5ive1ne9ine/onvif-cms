# Sentinel ONVIF CMS

一个从零实现的局域网 ONVIF 摄像头管理与视觉模型视频流检测系统。后端、控制台和数据库迁移打包在同一个 Java 21 应用中，不依赖旧项目代码。

## 已实现

- WS‑Discovery 多网卡组播搜索，自动发现局域网 ONVIF 摄像头并写入 MySQL。
- ONVIF WS‑Security PasswordDigest，同时兼容设备的 HTTP Basic/Digest challenge。
- 自动读取设备信息、Media Profile、RTSP 地址与 PTZ 能力。
- FFmpeg 将 RTSP 转成浏览器可直接预览的 MJPEG。
- ONVIF ContinuousMove/Stop 云台控制，支持上下左右和变焦。
- 从实时流连续采样画面，可调用 Mage‑VL/SGLang 或本机 Ollama 视觉模型做语义事件检测。
- 每台摄像头独立配置检测提示词、周期和置信度阈值。
- 检测事件持久化、SSE 实时推送和中文响应式管理界面。
- 摄像头密码使用 AES‑256‑GCM 加密保存；数据库结构由 Flyway 自动初始化。

## 技术设计

运行时采用 Java 21、Spring Boot 3.5、Spring Data JPA、MySQL 8、Flyway 和 FFmpeg。前端是随 JAR 发布的原生 HTML/CSS/JavaScript，不需要 Node.js 或额外的前端构建流程。后台阻塞型设备 I/O 和模型任务使用 Java 21 虚拟线程。

浏览器无法直接播放 RTSP，因此预览请求由 Java 启动受控的 FFmpeg 进程，通过 TCP 读取摄像头流并输出 MJPEG。检测使用同一 RTSP 源按时间顺序提取画面：Mage‑VL 走 SGLang 的 OpenAI 兼容接口；Ollama 走原生多图片接口并关闭思考输出。两种后端均要求结构化事件结果，后端再应用置信度阈值，避免把每次普通分析都写成事件。

## 运行要求

1. JDK 21 和 Maven 3.9，或者 Docker。
2. `ffmpeg` 可执行文件位于 `PATH`；Docker 镜像中已安装。
3. 应用主机与摄像头位于同一二层局域网，防火墙允许 UDP 3702 和摄像头的 HTTP/RTSP 端口。
4. MySQL 数据库 `onvif_cms` 已存在。应用会自动创建表，但不会自动创建数据库。
5. Mage‑VL/SGLang 或具备视觉能力的 Ollama 模型已启动。

项目已默认使用指定数据库：

```text
jdbc:mysql://192.168.100.101:23306/onvif_cms
username: root
password: admin123
```

生产环境建议通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖默认值。

## 使用本机 Ollama 4B

本机显存有限时可先使用 Ollama 的量化视觉模型。它不是微软 Mage‑VL，而是可直接运行的本地兼容后端；切换后界面会显示真实提供方和模型名：

```powershell
ollama pull qwen3.5:4b
```

项目根目录的 `application-local.yml` 会被自动读取且不会提交到 Git。本机配置示例：

```yaml
app:
  mage:
    provider: ollama
    base-url: http://localhost:11434/v1
    model: qwen3.5:4b
    max-frames: 4
    max-concurrent-analyses: 1
    request-timeout-seconds: 300
```

## 启动原生 Mage‑VL

按 [Mage‑VL 官方说明](https://github.com/microsoft/Mage/blob/main/mage_vl/README.md) 安装其 Mage‑VL SGLang 分支并启动服务：

```bash
git clone -b feat/mage-vl https://github.com/kcz358/sglang
cd sglang
pip install -e 'python[all]'
python -m sglang.launch_server \
  --model-path microsoft/Mage-VL \
  --trust-remote-code \
  --host 0.0.0.0 \
  --port 30000
```

模型为 BF16 权重，建议使用至少 16 GB NVIDIA 显存。服务的 OpenAI 兼容基地址应为 `http://<模型主机>:30000/v1`。

## 本机启动

Windows 上应先安装 JDK 21 与 FFmpeg。当前 PowerShell 会话确认版本后运行：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:APP_SECURITY_KEY = '请替换为长期固定的随机密钥'
$env:MAGE_BASE_URL = 'http://127.0.0.1:30000/v1'
mvn spring-boot:run
```

访问 <http://localhost:28080>。首次使用时点击“搜索设备”，为发现的摄像头填写 ONVIF 账号和密码。

`APP_SECURITY_KEY` 用于摄像头密码加密。一旦已经保存摄像头凭据，不要更换该值，否则旧密文无法解密。

## Docker 启动

跨平台普通网络模式：

```bash
cp .env.example .env
docker compose up --build -d
```

Docker bridge 网络通常无法可靠接收物理局域网的 WS‑Discovery 组播。Linux 部署建议使用主机网络版本：

```bash
docker compose -f compose.linux-host.yaml up --build -d
```

Windows Docker Desktop 若搜索不到设备，最可靠的方式是在 Windows 主机直接运行 JAR；实时预览和 PTZ 不受此限制，只要设备已发现并保存。

## 配置项

| 环境变量 | 默认值 | 作用 |
|---|---|---|
| `DB_URL` | 指定的局域网 MySQL | JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `admin123` | 数据库账号 |
| `APP_SECURITY_KEY` | 仅开发占位值 | 摄像头凭据加密密钥 |
| `FFMPEG_PATH` | `ffmpeg` | FFmpeg 可执行文件 |
| `MAGE_PROVIDER` | `sglang` | 推理后端：`sglang` 或 `ollama` |
| `MAGE_BASE_URL` | `http://localhost:30000/v1` | Mage‑VL OpenAI 兼容接口 |
| `MAGE_MODEL` | `microsoft/Mage-VL` | 模型名 |
| `MAGE_ENABLED` | `true` | 全局启停 AI 检测 |
| `DISCOVERY_INTERVAL_SECONDS` | `30` | 自动搜索周期 |
| `MAGE_SAMPLE_SECONDS` | `8` | 单次分析采样时长 |
| `MAGE_MAX_FRAMES` | `6` | 单次提交的最大画面数 |

## 主要 API

| 方法 | 地址 | 说明 |
|---|---|---|
| `POST` | `/api/discovery/scan` | 立即执行 WS‑Discovery |
| `GET` | `/api/cameras` | 摄像头列表 |
| `PUT` | `/api/cameras/{id}/credentials` | 保存凭据并读取 ONVIF 媒体配置 |
| `GET` | `/api/cameras/{id}/preview.mjpg` | 实时 MJPEG 预览 |
| `POST` | `/api/cameras/{id}/ptz/move` | 云台连续移动 |
| `PUT` | `/api/cameras/{id}/detection` | 配置视觉模型检测规则 |
| `POST` | `/api/cameras/{id}/detection/run` | 立即提交一次分析 |
| `GET` | `/api/events` | 查询检测事件 |
| `GET` | `/api/events/stream` | SSE 事件流 |

## 测试与构建

```bash
mvn clean test
mvn clean package
```

测试覆盖 WS‑Discovery 响应解析、JPEG 视频帧切分、视觉模型结构化响应解析和摄像头凭据加解密。
