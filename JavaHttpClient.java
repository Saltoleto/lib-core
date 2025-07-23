package com.sequentialapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sequentialapi.exception.ApiException;
import com.sequentialapi.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class JavaHttpClient implements ApiClient {

    private static final Logger logger = LoggerFactory.getLogger(JavaHttpClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JavaHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public <T> CompletableFuture<ApiResponse<T>> get(String url, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return sendRequest(request, responseType);
    }

    @Override
    public <T> CompletableFuture<ApiResponse<T>> post(String url, Object requestBody, Class<T> responseType) {
        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            return sendRequest(request, responseType);
        } catch (IOException e) {
            logger.error("Erro ao serializar o corpo da requisição POST para JSON: {}", e.getMessage());
            return CompletableFuture.failedFuture(new ApiException("Erro ao serializar o corpo da requisição", e));
        }
    }

    private <T> CompletableFuture<ApiResponse<T>> sendRequest(HttpRequest request, Class<T> responseType) {
        logger.info("Enviando requisição {} para {}", request.method(), request.uri());
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(httpResponse -> {
                    Map<String, String> headers = new HashMap<>();
                    httpResponse.headers().map().forEach((key, values) -> 
                        headers.put(key, String.join(", ", values))
                    );

                    if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                        try {
                            T body = objectMapper.readValue(httpResponse.body(), responseType);
                            return new ApiResponse<>(httpResponse.statusCode(), headers, body);
                        } catch (IOException e) {
                            logger.error("Erro ao desserializar a resposta da API para {}: {}", responseType.getName(), e.getMessage());
                            throw new ApiException("Erro ao desserializar a resposta da API", httpResponse.statusCode(), httpResponse.body(), e);
                        }
                    } else {
                        logger.error("Requisição falhou com status {}: {}", httpResponse.statusCode(), httpResponse.body());
                        throw new ApiException("Requisição falhou", httpResponse.statusCode(), httpResponse.body());
                    }
                })
                .exceptionally(e -> {
                    logger.error("Erro durante a requisição HTTP: {}", e.getMessage());
                    throw new ApiException("Erro de comunicação HTTP", e);
                });
    }
}

