package com.example.web.vo;

public enum ResultCode {

    SUCCESS(200, "操作成功"),

    FAIL(-1, "系统异常"),
    PARAM_ERROR(-2, "参数错误"),

    USER_NOT_FOUND(-1001, "用户不存在"),
    PASSWORD_ERROR(-1002, "密码错误"),
    CAPTCHA_ERROR(-1003, "验证码错误"),
    CAPTCHA_EXPIRED(-1004, "验证码已过期"),
    USERNAME_EXISTS(-1005, "用户名已存在"),
    TOKEN_INVALID(-1006, "Token无效"),
    TOKEN_EXPIRED(-1007, "Token已过期"),
    UNAUTHORIZED(-1008, "未登录"),
    FORBIDDEN(-1009, "权限不足"),
    QQ_LOGIN_ERROR(-1010, "QQ登录失败"),

    ORDER_NOT_FOUND(-2001, "订单不存在"),
    ORDER_STATUS_ERROR(-2002, "订单状态不允许此操作"),
    ORDER_DUPLICATE(-2003, "请勿重复提交订单"),
    CART_EMPTY(-2004, "购物车为空，无法下单"),
    PRICE_CHANGED(-2005, "价格已变动，请刷新重试"),
    AMOUNT_MISMATCH(-2006, "金额不匹配，请刷新重试"),
    ORDER_CANCEL_NOT_ALLOWED(-2007, "只能取消待支付订单"),
    ORDER_ADDRESS_NOT_ALLOWED(-2008, "当前订单状态不允许修改地址"),
    ORDER_REFUND_NOT_ALLOWED(-2009, "当前订单状态不允许退单"),
    CART_ITEM_NOT_FOUND(-2010, "购物车中无此商品"),
    CART_UPDATE_FAIL(-2011, "更新购物车失败"),
    CART_ADD_FAIL(-2012, "添加购物车失败"),

    PRODUCT_NOT_FOUND(-3001, "商品不存在"),
    PRODUCT_OFF_SHELF(-3002, "商品已下架"),
    PRODUCT_NAME_EMPTY(-3003, "商品名称不能为空"),
    NOT_PURCHASED(-3004, "只有购买过该商品的用户才能评价"),
    ALREADY_LIKED(-3005, "您已经评价过了，不能重复提交相同评价"),
    LIKE_TYPE_INVALID(-3006, "参数错误，likeType必须为1(赞)或-1(踩)"),
    REVIEW_STAR_INVALID(-3101, "星级评分必须在1-5之间"),
    REVIEW_PRODUCT_ID_EMPTY(-3102, "商品ID不能为空"),
    REVIEW_ALREADY_EXISTS(-3103, "您已经评价过该商品了"),
    REVIEW_NOT_FOUND(-3104, "评价不存在"),
    REVIEW_NOT_OWNER(-3105, "只能删除自己的评价"),
    REVIEW_TEXT_TOO_LONG(-3108, "评价内容不能超过500字"),
    PRODUCT_ID_EMPTY(-3106, "待更新的菜品ID不能为空"),
    PRODUCT_DELETE_NOT_OFF_SHELF(-3107, "请先下架该菜品再删除"),
    ID_NOT_FOUND(-3007, "商品ID不存在"),
    PIC_FILE_UPLOAD_ERROR(-6004, "图片上传异常"),

    FREE_ORDER_NO_EVENT(-4001, "当前没有进行中的免单活动"),
    FREE_ORDER_ALREADY_GRABBED(-4002, "您已经抢过本次免单名额了"),
    FREE_ORDER_SOLD_OUT(-4003, "免单名额已被抢完"),
    FREE_ORDER_EVENT_NOT_FOUND(-4004, "活动不存在"),
    FREE_ORDER_EVENT_ENDED(-4005, "活动已结束"),
    FREE_ORDER_EVENT_NOT_EDITABLE(-4006, "仅允许编辑未开始的活动"),
    FREE_ORDER_EVENT_NOT_DELETABLE(-4007, "仅允许删除未开始的活动"),
    FREE_ORDER_COUNT_INVALID(-4008, "免单名额必须大于0"),
    FREE_ORDER_MAX_AMOUNT_INVALID(-4009, "最大免单金额必须大于0"),
    FREE_ORDER_MIN_AMOUNT_INVALID(-4010, "最小免单金额必须大于0"),
    FREE_ORDER_MIN_OVER_MAX(-4011, "最小免单金额不能大于最大免单金额"),
    FREE_ORDER_TIME_INVALID(-4012, "活动开始和结束时间不能为空"),
    FREE_ORDER_TIME_ORDER_INVALID(-4013, "开始时间不能晚于结束时间"),
    FREE_ORDER_END_TIME_INVALID(-4014, "结束时间不能早于开始时间"),
    FREE_ORDER_COUPON_NO_EMPTY(-4020, "免单券号不能为空"),
    FREE_ORDER_COUPON_NOT_FOUND(-4021, "免单券不存在"),
    FREE_ORDER_COUPON_NOT_OWNER(-4022, "免单券不属于您"),
    FREE_ORDER_COUPON_USED(-4023, "免单券已使用"),
    FREE_ORDER_COUPON_EXPIRED(-4024, "免单活动已结束，券已过期"),

    SYSTEM_BUSY(-5000, "系统繁忙，请稍后重试"),
    ORDER_SERVICE_ERROR(-5001, "订单服务调用失败"),

    FILE_UPLOAD_FAILED(-6001, "文件上传失败"),
    FILE_TYPE_INVALID(-6002, "无效的文件类型"),
    FILE_EMPTY(-6003, "上传文件为空"),
    MINIO_UPLOAD_FAILED(-6005, "Minio文件上传失败"),

    ID_GEN_ERROR(-7001, "获取ID失败"),
    NET_ERROR(-7002, "网络异常"),
    GETONEID_ERROR(-7003, "获取单个id号错误"),
    GETMANYIDS_ERROR(-7004, "获取多个id号错误"),

    // ===== 客服模块 -8000 段 =====
    CS_SESSION_NOT_FOUND(-8001, "会话不存在"),
    CS_TICKET_NOT_FOUND(-8002, "工单不存在"),
    CS_QUICK_REPLY_NOT_FOUND(-8003, "快捷回复不存在"),
    CS_AGENT_NOT_ONLINE(-8004, "未找到客服状态，请先上线"),
    CS_AGENT_ID_EMPTY(-8005, "目标客服ID不能为空"),
    CS_SESSION_ID_EMPTY(-8006, "sessionId 不能为空"),
    CS_USER_ID_EMPTY(-8007, "用户ID不能为空"),
    CS_NOT_AI_SESSION(-8008, "当前会话不是 AI 客服接待"),
    CS_TICKET_ID_EMPTY(-8009, "工单ID不能为空"),
    CS_TICKET_TITLE_EMPTY(-8010, "工单标题不能为空"),
    CS_RATING_INVALID(-8011, "评分必须在1-5之间"),
    CS_ORDER_NO_EMPTY(-8012, "订单号不能为空"),
    CS_LOGISTICS_STATUS_EMPTY(-8013, "物流状态不能为空"),
    CS_LOGISTICS_STATUS_INVALID(-8014, "无效的物流状态"),
    CS_STATUS_EMPTY(-8015, "状态不能为空"),
    CS_STATUS_INVALID(-8016, "无效的状态"),
    CS_RESOLUTION_EMPTY(-8017, "解决类型不能为空"),
    CS_RESOLUTION_INVALID(-8018, "无效的解决类型"),
    CS_FILE_EMPTY(-8019, "文件不能为空"),
    CS_FILE_UPLOAD_FAILED(-8020, "上传失败"),
    CS_MESSAGE_EMPTY(-8021, "留言内容不能为空"),
    CS_QUICK_REPLY_TITLE_EMPTY(-8022, "标题不能为空"),
    CS_QUICK_REPLY_CONTENT_EMPTY(-8023, "内容不能为空"),
    CS_OPERATION_FAILED(-8024, "操作失败"),

    ALIPAY_CREATE_PAY_FAILED(-9001, "支付宝支付创建失败，请稍后重试"),
    ALIPAY_NOTIFY_VERIFY_FAILED(-9002, "支付宝异步通知验证失败"),
    ALIPAY_TRADE_NOT_FOUND(-9003, "支付宝交易不存在"),
    ALIPAY_ORDER_NOT_FOUND(-9004, "未找到对应订单");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() { return code; }
    public String getMessage() { return message; }
}