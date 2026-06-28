FROM golang:1.22-alpine AS builder
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /workspace
COPY go.work go.work.sum* ./
COPY shared/ ./shared/
COPY seat-service/ ./seat-service/
RUN cd seat-service && go mod download
RUN CGO_ENABLED=0 GOOS=linux go build -C seat-service -ldflags="-w -s" -o /binary .

FROM scratch
COPY --from=builder /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
COPY --from=builder /usr/share/zoneinfo /usr/share/zoneinfo
COPY --from=builder /binary /service
ENV TZ=Asia/Kolkata
EXPOSE 8084
ENTRYPOINT ["/service"]
