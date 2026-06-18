package com.library.membership.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CashfreeOrderResponse {
    @JsonProperty("cf_order_id")
    private String cfOrderId;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("payment_session_id")
    private String paymentSessionId;

    @JsonProperty("order_status")
    private String orderStatus;
}
