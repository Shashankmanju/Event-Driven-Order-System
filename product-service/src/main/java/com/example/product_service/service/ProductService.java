package com.example.product_service.service;

import com.example.product_service.dto.ProductAvailability;
import com.example.product_service.dto.ProductAvailabilityRequest;
import com.example.product_service.dto.ProductAvailabilityResponse;
import com.example.product_service.dto.ProductDto;
import com.example.product_service.entity.Product;
import com.example.product_service.exception.ResourceNotFoundException;
import com.example.product_service.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public ProductDto getProductById(Long id) {
      return productRepository.findById(id)
              .map(this::convertToDTO)
              .orElseThrow(() -> new RuntimeException("Product not found with id " + id));

    }

    public ProductDto createProduct(ProductDto productDTO) {
        Product product = convertToEntity(productDTO);
        Product savedProduct = productRepository.save(product);
        return convertToDTO(savedProduct);
    }

    public ProductDto updateProduct(Long id, ProductDto productDTO) {
        Product existingProduct= productRepository.findById(id).orElseThrow(()-> new RuntimeException("Product not found with id "+id));
        existingProduct.setName(productDTO.getName());
        existingProduct.setDescription(productDTO.getDescription());
        existingProduct.setQuantity(productDTO.getQuantity());
        existingProduct.setPrice(productDTO.getPrice());
        Product updatedProduct = productRepository.save(existingProduct);
        return convertToDTO(updatedProduct);
    }

    private ProductDto convertToDTO(Product product) {
        return new ProductDto(product.getId(), product.getName(), product.getDescription(), product.getSkuCode(), product.getPrice(), product.getQuantity());
    }

    private Product convertToEntity(ProductDto productDTO) {
        return new Product(productDTO.getId(), productDTO.getName(), productDTO.getDescription(), productDTO.getSkuCode(), productDTO.getPrice(), productDTO.getQuantity());
    }

    public ProductAvailabilityResponse checkProductAvailability(List<ProductAvailabilityRequest> products) {

        // Create response object normally (without builder)
        ProductAvailabilityResponse response = new ProductAvailabilityResponse();

        // Map each ProductAvailabilityRequest to ProductAvailability
        List<ProductAvailability> availabilityList = products.stream()
                .map(productRequest -> {
                    // Fetch product from database by SKU
                    Product product = productRepository.findBySkuCode(productRequest.getSkuCode())
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "Product not found with SKU code " + productRequest.getSkuCode()));

                    // Check if product quantity is enough
                    boolean available = product.getQuantity() >= productRequest.getQuantity();

                    // Return a ProductAvailability object
                    return new ProductAvailability(product.getSkuCode(), available);
                })
                .collect(Collectors.toList());

        // Set the list into the response object
        response.setProductAvailabilityList(availabilityList);

        return response;
    }



    /* ---------- Inventory updates (Kafka) ---------- */

    @Transactional
    public void reduceProductQuantity(String skuCode, int quantity) {

        Product product = productRepository.findBySkuCode(skuCode)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found"));

        if (product.getQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock for " + skuCode);
        }

        product.setQuantity(product.getQuantity() - quantity);
        productRepository.save(product);
    }

    @Transactional
    public void increaseProductQuantity(String skuCode, int quantity) {

        Product product = productRepository.findBySkuCode(skuCode)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found"));

        product.setQuantity(product.getQuantity() + quantity);
        productRepository.save(product);
    }
}
