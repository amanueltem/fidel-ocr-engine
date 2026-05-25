package com.amman.fidel_ocr_engine.exception;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleException(MethodArgumentNotValidException exp){
        Set<String> errors=new HashSet<>();
        exp.getBindingResult().getAllErrors()
                .forEach(
                        error->{
                            var errorMessage=error.getDefaultMessage();
                            errors.add(errorMessage);
                        }
                );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        new ExceptionResponse(null,null,null,errors,null)
                );
    }



    @ExceptionHandler(OperationNotPermittedException.class)
    public ResponseEntity<ExceptionResponse> handleException(OperationNotPermittedException exp){
        exp.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        new ExceptionResponse(
                                null,
                                "Internal error, contact the admin",
                                exp.getMessage(),null,null)
                );
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception exp){
        exp.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        new ExceptionResponse(
                                null,
                                "Internal error, contact the admin",
                                exp.getMessage(),null,null)
                );
    }





}