package com.sequentialapi.client;

import com.sequentialapi.model.ApiResponse;
import java.util.concurrent.CompletableFuture;

public interface ApiClient {
    <T> CompletableFuture<ApiResponse<T>> get(String url, Class<T> responseType);
    <T> CompletableFuture<ApiResponse<T>> post(String url, Object requestBody, Class<T> responseType);
    // Outros métodos HTTP podem ser adicionados conforme necessário (put, delete, etc.)
}


