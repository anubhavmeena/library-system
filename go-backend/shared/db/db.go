package db

import (
	"fmt"
	"os"
	"time"

	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
)

func NewDB() *sqlx.DB {
	dsn := os.Getenv("DB_URL")
	if dsn == "" {
		host := getenv("DB_HOST", "localhost")
		port := getenv("DB_PORT", "5432")
		user := getenv("DB_USER", "library_user")
		pass := getenv("DB_PASSWORD", "library_pass")
		name := getenv("DB_NAME", "library_db")
		dsn = fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
			host, port, user, pass, name)
	}

	d, err := sqlx.Connect("postgres", dsn)
	if err != nil {
		panic("failed to connect to database: " + err.Error())
	}
	d.SetMaxOpenConns(10)
	d.SetMaxIdleConns(2)
	d.SetConnMaxLifetime(5 * time.Minute)
	return d
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
