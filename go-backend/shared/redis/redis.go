package redis

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"github.com/redis/go-redis/v9"
)

func NewClient() *redis.Client {
	host := getenv("REDIS_HOST", "localhost")
	portStr := getenv("REDIS_PORT", "6379")
	port, _ := strconv.Atoi(portStr)
	if port == 0 {
		port = 6379
	}

	client := redis.NewClient(&redis.Options{
		Addr: fmt.Sprintf("%s:%d", host, port),
	})

	if err := client.Ping(context.Background()).Err(); err != nil {
		panic("failed to connect to redis: " + err.Error())
	}
	return client
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
