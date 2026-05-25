package com.amman.fidel_ocr_engine.exception;

import org.springframework.http.HttpStatus;

public enum BusinessErrorCodes {

    NO_CODE(0, HttpStatus.NOT_IMPLEMENTED, "No code"),
    ACCOUNT_LOCKED(302, HttpStatus.FORBIDDEN, "User account is locked."),
    ACCOUNT_DISABLED(303, HttpStatus.FORBIDDEN, "User account is disabled."),
    BAD_CREDENTIALS(304, HttpStatus.FORBIDDEN, "Login and/or password is incorrect"),
    JWT_EXPIRED(305,HttpStatus.NOT_ACCEPTABLE,"jwt expired.");


    private final int code;


    private final String description;


    private final HttpStatus httpStatus;

     BusinessErrorCodes(int code, HttpStatus httpStatus, String description) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.description = description;
    }
    public  int getCode(){
         return code;
    }
    public String getDescription(){
         return  description;
    }
    public  HttpStatus getHttpStatus(){
         return  httpStatus;
    }
}
