package com.products.crud.service;

import com.products.crud.DTOs.ProductRequest;
import com.products.crud.DTOs.ProductResponse;
import com.products.crud.exception.InsufficientStockException;
import com.products.crud.exception.ProductNotFoundException;
import com.products.crud.exception.ProductServiceException;
import com.products.crud.models.Product;
import com.products.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;





    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "productPages", allEntries = true),
            @CacheEvict(cacheNames = "lowStockProducts", allEntries = true)
    })
    public ProductResponse createProduct(ProductRequest productRequest) {

        if(productRepository.findByName(productRequest.getName()).isPresent()) {
            throw new ProductServiceException("Product with name " + productRequest.getName() + " already exists.");
        }
        Product product = new Product();
        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setStockQuantity(productRequest.getStockQuantity());
        product.setLowStockThreshold(productRequest.getLowStockThreshold()
        != null ? productRequest.getLowStockThreshold() : 0);

        try{
            Product savedProduct = productRepository.save(product);
            return mapToProductResponse(savedProduct);
        }
        catch (DataIntegrityViolationException e){
            log.error("Data integrity violation while creating product: {}", e.getMessage());
            throw new ProductServiceException("Failed to create product due to data integrity violation.");
        }
        catch (Exception e){
            log.error("Unexpected error while creating product: {}", e.getMessage());
            throw new ProductServiceException("Failed to create product due to an unexpected error.");
        }
    }




    @Cacheable(cacheNames = "productPages",
            key = "'page-' + #pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(this::mapToProductResponse);
    }




    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "lowStockProducts", key = "'lowStock'")
    public List<ProductResponse> getLowStockProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream()
                .filter(p -> p.getStockQuantity() <= p.getLowStockThreshold())
                .map(this::mapToProductResponse)
                .toList();
    }





    @Transactional
    @Caching(
            put = @CachePut(cacheNames = "productById", key = "#id"),
            evict = {
                    @CacheEvict(cacheNames = "productPages", allEntries = true),
                    @CacheEvict(cacheNames = "lowStockProducts", allEntries = true)
            }
    )
    public ProductResponse updateProduct(UUID id, ProductRequest request){
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(()-> new ProductNotFoundException("Product not found with id: "+id)
                );

        if(!existingProduct.getName().equals(request.getName()) &&
        productRepository.findByName(request.getName()).isPresent()){
            throw new ProductServiceException("product with name " + request.getName() + "already exists.");
        }

        existingProduct.setName(request.getName());
        existingProduct.setDescription(request.getDescription());

        if(request.getStockQuantity()<0){
            throw new InsufficientStockException("stock quantity cannot be negative for product update.");
        }
        existingProduct.setStockQuantity(request.getStockQuantity());
        existingProduct.setLowStockThreshold(request.getLowStockThreshold()!=null ?
                request.getLowStockThreshold() :0
                );
        try{
            Product updateProduct = productRepository.save(existingProduct);
            return mapToProductResponse(updateProduct);
        }
        catch(OptimisticLockingFailureException e){
            log.warn("Optimistic locking failure during product update for id: {}",id);
            throw new ProductServiceException("failed to update product due to concurrent modification. please try again");
        }
        catch (DataIntegrityViolationException e){
            log.error("Data Integrity violation during product update for id: {}",id);
            throw new ProductServiceException("could not update product due to data conflict.");
        }
    }




    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5, backoff = @Backoff(delay = 100))
    @Caching(
            put = @CachePut(cacheNames = "productById", key = "#id"),
            evict = {
                    @CacheEvict(cacheNames = "productPages", allEntries = true),
                    @CacheEvict(cacheNames = "lowStockProducts", allEntries = true)
            }
    )
    public ProductResponse increaseStock(UUID id, Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to increase must be positive.");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        product.setStockQuantity(product.getStockQuantity() + quantity);
        try {
            Product updatedProduct = productRepository.save(product);
            log.info("Increased stock for product {} by {}. New quantity: {}", id, quantity, updatedProduct.getStockQuantity());
            return mapToProductResponse(updatedProduct);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure increasing stock for product {}. Retrying...", id);
            throw e; // Re-throw to trigger @Retryable
        }
    }




    @Transactional
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5, backoff = @Backoff(delay = 100))
    @Caching(
            put = @CachePut(cacheNames = "productById", key = "#id"),
            evict = {
                    @CacheEvict(cacheNames = "productPages", allEntries = true),
                    @CacheEvict(cacheNames = "lowStockProducts", allEntries = true)
            }
    )
    public ProductResponse decreaseStock(UUID id, Integer quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to decrease must be positive.");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));

        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException("Insufficient stock for product " + product.getName() +
                    ". Available: " + product.getStockQuantity() + ", Requested: " + quantity);
        }

        product.setStockQuantity(product.getStockQuantity() - quantity);
        try {
            Product updatedProduct = productRepository.save(product);
            log.info("Decreased stock for product {} by {}. New quantity: {}", id, quantity, updatedProduct.getStockQuantity());
            return mapToProductResponse(updatedProduct);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure decreasing stock for product {}. Retrying...", id);
            throw e; // Re-throw to trigger @Retryable
        }
    }




    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "productById", key = "#id"),
            @CacheEvict(cacheNames = "productPages", allEntries = true),
            @CacheEvict(cacheNames = "lowStockProducts", allEntries = true)
    })
    public void deleteProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }





    private ProductResponse mapToProductResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setStockQuantity(product.getStockQuantity());
        response.setLowStockThreshold(product.getLowStockThreshold());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        return response;
    }



    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "productById", key = "#id")
    public ProductResponse getProductById(UUID id) {
        return productRepository.findById(id)
                .map(this::mapToProductResponse)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
    }
}
