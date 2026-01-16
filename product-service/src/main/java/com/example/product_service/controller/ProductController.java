package com.example.product_service.controller;

import com.example.product_service.dto.ProductAvailabilityRequest;
import com.example.product_service.dto.ProductAvailabilityResponse;
import com.example.product_service.dto.ProductDto;
import com.example.product_service.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/{id}")
    public ProductDto getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PostMapping
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto productDTO) {
        ProductDto createdProduct = productService.createProduct(productDTO);
        return ResponseEntity.ok(createdProduct);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable Long id, @RequestBody ProductDto productDTO) {
        ProductDto updatedProduct = productService.updateProduct(id, productDTO);
        return ResponseEntity.ok(updatedProduct);
    }

    /*@PostMapping("/availability")
    private ResponseEntity<ProductAvailabilityResponse>checkProductAvailability(@RequestBody List<ProductDTO> products) {
        ProductAvailabilityResponse availabilityList = productService.checkProductAvailability(products);
        return ResponseEntity.ok(availabilityList);
    }*/



    @PostMapping("/availability")
    public ResponseEntity<ProductAvailabilityResponse>
    checkProductAvailability(
            @RequestBody List<ProductAvailabilityRequest> products) {

        return ResponseEntity.ok(
                productService.checkProductAvailability(products)
        );
    }

    //Request → list of ProductDTO (client tells us what they want).
    //Response → ProductAvailabilityResponse (server tells client what is actually available).

}
