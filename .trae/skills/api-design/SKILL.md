---
name: api-design
description: RESTful API design best practices and conventions guide
---

# API Design Skill

This skill provides comprehensive guidance for designing RESTful APIs following industry best practices.

## Core Principles

### 1. Resource-Oriented Design
- Use nouns for resource names (e.g., `/users`, `/products`)
- Avoid verbs in URLs
- Use HTTP methods to represent actions

### 2. HTTP Methods
- **GET**: Retrieve resources
- **POST**: Create new resources
- **PUT**: Update entire resources
- **PATCH**: Partial updates
- **DELETE**: Remove resources

### 3. URL Structure
```
GET    /api/v1/users          - List all users
GET    /api/v1/users/{id}     - Get specific user
POST   /api/v1/users          - Create new user
PUT    /api/v1/users/{id}     - Update user
DELETE /api/v1/users/{id}     - Delete user
```

### 4. Response Format
- Use JSON as default format
- Use camelCase for field names
- Include metadata (pagination, timestamps)

### 5. Error Handling
```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "User ID must be a positive integer",
    "details": []
  }
}
```

### 6. Status Codes
- 200: Success
- 201: Created
- 400: Bad Request
- 401: Unauthorized
- 404: Not Found
- 500: Internal Server Error

## Best Practices
- Version your APIs
- Use pagination for list endpoints
- Implement rate limiting
- Document with OpenAPI/Swagger
