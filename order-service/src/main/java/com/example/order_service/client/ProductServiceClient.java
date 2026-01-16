package com.example.order_service.client;

import com.example.order_service.dto.OrderItemDto;
import com.example.order_service.dto.ProductAvailability;
import com.example.order_service.dto.ProductAvailabilityRequest;
import com.example.order_service.dto.ProductAvailabilityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class ProductServiceClient {

    // RestTemplate is used to make HTTP calls to other microservices
    private final RestTemplate restTemplate;

    // Base URL of Product Service (loaded from application.yml / properties)
    @Value("${product.service.url}")
    private String productServiceUrl;

    // Constructor injection (recommended best practice)
    public ProductServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Calls Product Service to check availability of products
     *
     * @param orderItems List of OrderItemDto from Order Service
     * @return List of ProductAvailability (skuCode + available flag)
     */
    public List<ProductAvailability> checkProductAvailability(List<OrderItemDto> orderItems) {

        /*
         * STEP 1: Convert OrderItemDto → ProductAvailabilityRequest
         *
         * WHY?
         * - OrderItemDto contains extra fields (price, name, etc.)
         * - Product Service only needs skuCode + quantity
         *
         * This list becomes the REQUEST BODY sent to Product Service
         */
        List<ProductAvailabilityRequest> request =
                orderItems.stream()
                        .map(item ->
                                new ProductAvailabilityRequest(
                                        item.getSkuCode(),   // product identifier
                                        item.getQuantity()   // required quantity
                                ))
                        .toList();

        /*
         * STEP 2: Build the complete Product Service URL
         * Example:
         * http://localhost:8051/api/products/availability
         */
        String url = productServiceUrl + "/availability";

        /*
         * STEP 3: Prepare HTTP headers
         * We specify JSON since we are sending JSON payload
         */
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        /*
         * STEP 4: Create HttpEntity
         * - Contains request body
         * - Contains headers
         */
        HttpEntity<List<ProductAvailabilityRequest>> entity =
                new HttpEntity<>(request, headers);

        try {
            /*
             * STEP 5: Make REST call to Product Service
             *
             * - HTTP Method: POST
             * - Request Body: List<ProductAvailabilityRequest>
             * - Response Type: ProductAvailabilityResponse
             */
            ResponseEntity<ProductAvailabilityResponse> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            entity,
                            ProductAvailabilityResponse.class
                    );

            /*
             * STEP 6: Extract and return product availability list
             *
             * ProductAvailabilityResponse contains:
             * List<ProductAvailability> productAvailabilityList
             */
            return response.getBody().getProductAvailabilityList();

        } catch (Exception e) {
            /*
             * STEP 7: Handle failures
             * - Product Service down
             * - Timeout
             * - Network issue
             */
            log.error("Error calling Product Service", e);

            // Return empty list so Order Service can handle gracefully
            return List.of();
        }
    }
}


/*Request to Product Service (from Order Service):
[
  {
    "skuCode": "IPHONE_15",
    "quantity": 2
  },
  {
    "skuCode": "AIRPODS",
    "quantity": 1
  }
]

* Response from the product service */
/*{
        "productAvailabilityList": [
        {
        "skuCode": "IPHONE_15",
        "available": true
        },
        {
        "skuCode": "AIRPODS",
        "available": false
        }
        ]
        }
*/