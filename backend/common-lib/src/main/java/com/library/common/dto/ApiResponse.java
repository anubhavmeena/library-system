package com.library.common.dto;

import java.time.Instant;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String timestamp;

    public ApiResponse() {}

    public ApiResponse(boolean success, String message, T data, String timestamp) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data, Instant.now().toString());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, Instant.now().toString());
    }

    public boolean isSuccess()      { return success; }
    public String getMessage()      { return message; }
    public T getData()              { return data; }
    public String getTimestamp()    { return timestamp; }

    public void setSuccess(boolean success)     { this.success = success; }
    public void setMessage(String message)      { this.message = message; }
    public void setData(T data)                 { this.data = data; }
    public void setTimestamp(String timestamp)  { this.timestamp = timestamp; }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    public static class Builder<T> {
        private boolean success;
        private String message;
        private T data;
        private String timestamp;

        public Builder<T> success(boolean success)   { this.success = success; return this; }
        public Builder<T> message(String message)    { this.message = message; return this; }
        public Builder<T> data(T data)               { this.data = data; return this; }
        public Builder<T> timestamp(String timestamp){ this.timestamp = timestamp; return this; }
        public ApiResponse<T> build() { return new ApiResponse<>(success, message, data, timestamp); }
    }
}
