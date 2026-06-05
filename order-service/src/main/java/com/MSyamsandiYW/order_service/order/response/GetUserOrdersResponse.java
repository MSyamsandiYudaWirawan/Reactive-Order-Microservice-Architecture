package com.MSyamsandiYW.order_service.order.response;


import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class GetUserOrdersResponse {
    private List<GetStatusOrderResponse> orders;
}
