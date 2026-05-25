package com.amman.fidel_ocr_engine.exception;

public class OperationNotPermittedException extends RuntimeException {
    public OperationNotPermittedException(String msg ){
        super(msg);
    }
}
