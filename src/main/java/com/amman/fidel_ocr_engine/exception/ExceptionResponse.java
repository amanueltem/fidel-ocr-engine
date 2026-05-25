package com.amman.fidel_ocr_engine.exception;

import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonInclude;



@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record  ExceptionResponse(
        Integer businessErrorCode,
        String businessErrorDescription,
         String error,
       Set<String> validationErrors,
        Map<String,String> errors
) {

}