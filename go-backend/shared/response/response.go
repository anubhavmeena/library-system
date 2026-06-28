package response

import "time"

type ApiResponse[T any] struct {
	Success   bool      `json:"success"`
	Message   string    `json:"message,omitempty"`
	Data      T         `json:"data,omitempty"`
	Timestamp time.Time `json:"timestamp"`
}

func Success[T any](data T) ApiResponse[T] {
	return ApiResponse[T]{Success: true, Data: data, Timestamp: time.Now()}
}

func SuccessMsg(msg string) ApiResponse[any] {
	return ApiResponse[any]{Success: true, Message: msg, Timestamp: time.Now()}
}

func Fail(msg string) ApiResponse[any] {
	return ApiResponse[any]{Success: false, Message: msg, Timestamp: time.Now()}
}
