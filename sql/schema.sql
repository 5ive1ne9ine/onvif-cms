CREATE DATABASE IF NOT EXISTS onvif_cms DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE onvif_cms;

-- 摄像头
CREATE TABLE camera (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    name             VARCHAR(128) NOT NULL COMMENT '摄像头名称',
    ip               VARCHAR(64)  NOT NULL COMMENT 'IP地址',
    onvif_port       INT          NOT NULL DEFAULT 80 COMMENT 'ONVIF端口',
    username         VARCHAR(64) COMMENT 'ONVIF用户名',
    password         VARCHAR(128) COMMENT 'ONVIF密码(AES加密)',
    manufacturer     VARCHAR(64) COMMENT '厂商',
    model            VARCHAR(64) COMMENT '型号',
    serial_no        VARCHAR(64) COMMENT '序列号',
    firmware         VARCHAR(64) COMMENT '固件版本',
    main_rtsp_url    VARCHAR(512) COMMENT '主码流RTSP地址',
    sub_rtsp_url     VARCHAR(512) COMMENT '子码流RTSP地址',
    ptz_supported    TINYINT(1) DEFAULT 0 COMMENT '是否支持PTZ',
    events_supported TINYINT(1) DEFAULT 0 COMMENT '是否支持事件',
    status           VARCHAR(16) DEFAULT 'OFFLINE' COMMENT '状态: ONLINE/OFFLINE/ERROR',
    enabled          TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ip_port (ip, onvif_port)
) COMMENT '摄像头表';

-- 事件规则
CREATE TABLE event_rule (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_id     BIGINT       NOT NULL COMMENT '摄像头ID',
    topic         VARCHAR(256) NOT NULL COMMENT 'ONVIF事件Topic',
    rule_name     VARCHAR(128) NOT NULL COMMENT '规则名称',
    record_video  TINYINT(1) DEFAULT 1 COMMENT '触发时录制视频',
    snapshot      TINYINT(1) DEFAULT 1 COMMENT '触发时抓图',
    pre_seconds   INT DEFAULT 5 COMMENT '预录秒数',
    post_seconds  INT DEFAULT 15 COMMENT '延录秒数',
    enabled       TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_camera (camera_id)
) COMMENT '事件规则表';

-- 事件日志
CREATE TABLE event_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_id     BIGINT       NOT NULL COMMENT '摄像头ID',
    rule_id       BIGINT COMMENT '触发规则ID',
    topic         VARCHAR(256) COMMENT '事件Topic',
    source        VARCHAR(256) COMMENT '事件来源',
    payload_json  TEXT COMMENT '原始事件载荷JSON',
    occurred_at   DATETIME(3)  NOT NULL COMMENT '事件发生时间',
    recording_id  BIGINT COMMENT '关联录制ID',
    snapshot_path VARCHAR(512) COMMENT '截图文件路径',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_cam_time (camera_id, occurred_at)
) COMMENT '事件日志表';

-- 录制记录
CREATE TABLE recording (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    camera_id   BIGINT       NOT NULL COMMENT '摄像头ID',
    type        VARCHAR(16)  NOT NULL COMMENT '类型: EVENT/MANUAL/SCHEDULED',
    file_path   VARCHAR(512) NOT NULL COMMENT '文件路径',
    file_size   BIGINT COMMENT '文件大小(字节)',
    start_time  DATETIME(3)  NOT NULL COMMENT '开始时间',
    end_time    DATETIME(3) COMMENT '结束时间',
    duration_ms BIGINT COMMENT '时长(毫秒)',
    status      VARCHAR(16) DEFAULT 'RECORDING' COMMENT '状态: RECORDING/COMPLETED/FAILED',
    event_id    BIGINT COMMENT '关联事件ID',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_cam_start (camera_id, start_time)
) COMMENT '录制记录表';

-- 系统用户
CREATE TABLE system_user (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    username   VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password   VARCHAR(128) NOT NULL COMMENT '密码(BCrypt)',
    nickname   VARCHAR(64) COMMENT '昵称',
    role       VARCHAR(16) DEFAULT 'ADMIN' COMMENT '角色',
    enabled    TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) COMMENT '系统用户表';

-- 默认管理员 (密码: admin123)
INSERT INTO system_user(username, password, nickname, role)
VALUES('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Administrator', 'ADMIN');
