package com.example.message;

public enum MessageType {
    /** 普通聊天消息 */
    MESSAGE,
    /** 命令消息（如搜索商品、查询订单等） */
    COMMAND,
    /** 系统通知（如公告、提示等） */
    SYSTEM,
    /** 事件消息（如订单状态变更等） */
    EVENT,
    /** 用户上线通知 */
    USER_ONLINE,
    /** 用户下线通知 */
    USER_OFFLINE,
    /** 在线用户列表 */
    USER_LIST,
    /** 会话创建通知 */
    SESSION_CREATED,
    /** 会话关闭通知 */
    SESSION_CLOSED,
    /** 会话转接通知 */
    SESSION_TRANSFERRED,
    /** 正在输入提示 */
    TYPING,
    /** 已读回执 */
    READ_RECEIPT,
    /** 心跳包（保持连接） */
    HEARTBEAT
}