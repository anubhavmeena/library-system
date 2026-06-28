FROM golang:1.22-alpine AS builder
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /workspace
COPY go.work go.work.sum* ./
COPY shared/ ./shared/
COPY user-service/ ./user-service/
RUN cd user-service && go mod download
RUN CGO_ENABLED=0 GOOS=linux go build -C user-service -ldflags="-w -s" -o /binary .

FROM alpine:3.19
RUN apk add --no-cache ca-certificates tzdata
COPY --from=builder /binary /service
ENV TZ=Asia/Kolkata
EXPOSE 8082
ENTRYPOINT ["/service"]
