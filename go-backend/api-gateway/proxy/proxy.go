package proxy

import (
	"encoding/json"
	"net/http"
	"net/http/httputil"
	"net/url"

	"github.com/gin-gonic/gin"
	"library/shared/response"
)

func To(target string) gin.HandlerFunc {
	parsed, err := url.Parse(target)
	if err != nil {
		panic("invalid proxy target: " + target)
	}

	rp := httputil.NewSingleHostReverseProxy(parsed)
	rp.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadGateway)
		json.NewEncoder(w).Encode(response.Fail("upstream unavailable: " + err.Error()))
	}

	return func(c *gin.Context) {
		// Rewrite the host so upstream sees the correct Host header.
		c.Request.Host = parsed.Host
		// Strip double slash that can appear when Gin captures "/*path"
		rp.ServeHTTP(c.Writer, c.Request)
	}
}
