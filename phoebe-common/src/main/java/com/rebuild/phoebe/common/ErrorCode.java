package com.rebuild.phoebe.common;

/** 错误码。原项目错误码体系 [U]，此处为新建实现。 */
public final class ErrorCode {
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int ILLEGAL_STATE = 409;
    public static final int LIMIT_EXCEEDED = 422;
    public static final int INSUFFICIENT_FUNDS = 402;
    public static final int SERVER_ERROR = 500;

    private ErrorCode() {
    }
}
