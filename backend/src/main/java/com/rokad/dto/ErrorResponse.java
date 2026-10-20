package com.rokad.dto;

public record ErrorResponse(String error, String field, String message) {

  public static ErrorResponse of(String error, String message) {
    return new ErrorResponse(error, null, message);
  }

  public static ErrorResponse of(String error, String field, String message) {
    return new ErrorResponse(error, field, message);
  }
}
