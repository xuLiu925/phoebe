package com.rebuild.phoebe.common;

import lombok.Getter;

/** 业务异常。code 取值见 {@link ErrorCode}。 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public static BizException notFound(String what) {
        return new BizException(ErrorCode.NOT_FOUND, what + "不存在");
    }

    public static BizException forbidden() {
        return new BizException(ErrorCode.FORBIDDEN, "无权访问该数据");
    }

    public static BizException limit(String message) {
        return new BizException(ErrorCode.LIMIT_EXCEEDED, message);
    }

    public static BizException state(String message) {
        return new BizException(ErrorCode.ILLEGAL_STATE, message);
    }
}
