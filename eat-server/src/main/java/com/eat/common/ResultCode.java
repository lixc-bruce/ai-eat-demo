package com.eat.common;

public interface ResultCode {

    int SUCCESS = 200;
    int BAD_REQUEST = 400;
    int UNAUTHORIZED = 401;
    int FORBIDDEN = 403;
    int NOT_FOUND = 404;
    int TOO_MANY_REQUESTS = 429;
    int UNPROCESSABLE_ENTITY = 422;
    int INTERNAL_ERROR = 500;
    int SERVICE_UNAVAILABLE = 503;
}
