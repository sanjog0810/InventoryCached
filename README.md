🚀 Blazing-Fast Inventory Management API
Welcome to a production-ready, high-performance REST API for inventory management, built with Spring Boot and supercharged with Redis for lightning-fast data retrieval.

This project is designed to handle high-concurrency environments with grace, ensuring data integrity and providing a seamless developer experience through comprehensive API documentation. It's more than just a CRUD application; it's a blueprint for building scalable and resilient microservices.

✨ Key Features
High-Speed Caching: Integrated with Redis (@Cacheable, @CachePut, @CacheEvict) to dramatically reduce database load and deliver sub-millisecond response times for frequent read operations.

Concurrency-Safe Operations: Implements Optimistic Locking (@Version) and automatic Retry Mechanisms (@Retryable) to handle race conditions and prevent data corruption during simultaneous stock updates.

Robust Error Handling: A centralized, global exception handler with custom exceptions (ProductNotFoundException, InsufficientStockException) provides clear, meaningful, and consistent error responses.

Interactive API Documentation: Pre-configured Swagger UI allows for easy exploration, testing, and understanding of all available endpoints right from your browser.

Clean & Scalable Architecture: Follows industry best practices with a clear separation of concerns (Controller, Service, Repository layers) and uses the Data Transfer Object (DTO) pattern to decouple the API from the database model.

Full CRUD Functionality: Comprehensive endpoints for creating, reading, updating, and deleting products, including paginated results for fetching all products.

Advanced Stock Management: Specialized endpoints to safely increase or decrease stock levels, crucial for e-commerce or warehouse systems.

🛠️ Tech Stack
Framework: Spring Boot

Language: Java

Database: PostgreSQL / H2 (for testing)

Caching: Redis

Data Access: Spring Data JPA / Hibernate

API Documentation: Springdoc OpenAPI (Swagger UI)

Build Tool: Maven / Gradle

Concurrency: Spring Retry & JPA Optimistic Locking

🔌 API Endpoints Overview
The API provides a full suite of endpoints to manage products:

Method	Endpoint	Description
POST	/api/products	Create a new product.
GET	/api/products	Get all products (with pagination).
GET	/api/products/{id}	Get a single product by its ID.
GET	/api/products/low-stock	Get a list of products below their stock threshold.
PUT	/api/products/{id}	Update an existing product.
PATCH	/api/products/{id}/increase-stock	Increase the stock for a product.
PATCH	/api/products/{id}/decrease-stock	Decrease the stock for a product.
DELETE	/api/products/{id}	Delete a product.
