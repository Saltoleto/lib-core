package com.sequentialapi.service;

import com.sequentialapi.client.ApiClient;
import com.sequentialapi.exception.ApiException;
import com.sequentialapi.model.ApiRequest;
import com.sequentialapi.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Implementação do serviço de chamadas sequenciais de API.
 * Utiliza threads virtuais do Java 21 para melhor performance.
 * Segue os princípios SOLID:
 * - SRP: Responsável apenas pela orquestração de chamadas sequenciais
 * - OCP: Aberto para extensão através de interfaces
 * - LSP: Implementa corretamente a interface SequentialApiService
 * - ISP: Interface segregada com métodos específicos
 * - DIP: Depende de abstrações (ApiClient) não de implementações concretas
 */
public class SequentialApiServiceImpl implements SequentialApiService {

    private static final Logger logger = LoggerFactory.getLogger(SequentialApiServiceImpl.class);
    
    private final ApiClient apiClient;
    private final Executor virtualThreadExecutor;

    public SequentialApiServiceImpl(ApiClient apiClient) {
        this.apiClient = apiClient;
        // Usando threads virtuais para melhor performance em I/O
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public <T> CompletableFuture<ApiResponse<T>> executeSequentialCalls(
            ApiRequest initialRequest,
            List<Function<ApiResponse<?>, ApiRequest>> requestBuilders,
            Class<T> finalResponseType) {
        
        logger.info("Iniciando execução sequencial de {} chamadas de API", requestBuilders.size() + 1);
        
        return CompletableFuture
                .supplyAsync(() -> {
                    logger.debug("Executando primeira chamada: {}", initialRequest.getUrl());
                    return executeSingleCall(initialRequest, Object.class).join();
                }, virtualThreadExecutor)
                .thenCompose(firstResponse -> {
                    if (!firstResponse.isSuccess()) {
                        logger.error("Primeira chamada falhou com status: {}", firstResponse.getStatusCode());
                        return CompletableFuture.failedFuture(
                            new ApiException("Primeira chamada falhou", firstResponse.getStatusCode(), 
                                           firstResponse.getBody() != null ? firstResponse.getBody().toString() : null)
                        );
                    }
                    
                    return executeRemainingCalls(firstResponse, requestBuilders, finalResponseType);
                });
    }

    @Override
    public <T> CompletableFuture<ApiResponse<T>> executeSingleCall(ApiRequest request, Class<T> responseType) {
        logger.debug("Executando chamada única para: {}", request.getUrl());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<ApiResponse<T>> futureResponse;
                
                switch (request.getMethod().toUpperCase()) {
                    case "GET":
                        futureResponse = apiClient.get(request.getUrl(), responseType);
                        break;
                    case "POST":
                        futureResponse = apiClient.post(request.getUrl(), request.getBody(), responseType);
                        break;
                    default:
                        throw new ApiException("Método HTTP não suportado: " + request.getMethod());
                }
                
                return futureResponse.join();
                
            } catch (Exception e) {
                logger.error("Erro durante execução da chamada para {}: {}", request.getUrl(), e.getMessage());
                // Se a exceção já for ApiException, relança para preservar os detalhes
                if (e.getCause() instanceof ApiException) {
                    throw (ApiException) e.getCause();
                } else {
                    throw new ApiException("Erro durante execução da chamada", e);
                }
            }
        }, virtualThreadExecutor);
    }

    private <T> CompletableFuture<ApiResponse<T>> executeRemainingCalls(
            ApiResponse<?> previousResponse,
            List<Function<ApiResponse<?>, ApiRequest>> requestBuilders,
            Class<T> finalResponseType) {
        
        if (requestBuilders.isEmpty()) {
            // Se não há mais chamadas, retorna a resposta anterior convertida para o tipo final
            return CompletableFuture.completedFuture(convertResponse(previousResponse, finalResponseType));
        }
        
        // Pega o primeiro builder da lista
        Function<ApiResponse<?>, ApiRequest> nextRequestBuilder = requestBuilders.get(0);
        List<Function<ApiResponse<?>, ApiRequest>> remainingBuilders = requestBuilders.subList(1, requestBuilders.size());
        
        return CompletableFuture
                .supplyAsync(() -> {
                    ApiRequest nextRequest = nextRequestBuilder.apply(previousResponse);
                    logger.debug("Executando próxima chamada: {}", nextRequest.getUrl());
                    return nextRequest;
                }, virtualThreadExecutor)
                .thenCompose(nextRequest -> {
                    if (remainingBuilders.isEmpty()) {
                        // Esta é a última chamada, usa o tipo final
                        return executeSingleCall(nextRequest, finalResponseType);
                    } else {
                        // Ainda há mais chamadas, usa Object como tipo intermediário
                        return executeSingleCall(nextRequest, Object.class)
                                .thenCompose(response -> {
                                    if (!response.isSuccess()) {
                                        logger.error("Chamada intermediária falhou com status: {}", response.getStatusCode());
                                        return CompletableFuture.failedFuture(
                                            new ApiException("Chamada intermediária falhou", response.getStatusCode(),
                                                           response.getBody() != null ? response.getBody().toString() : null)
                                        );
                                    }
                                    return executeRemainingCalls(response, remainingBuilders, finalResponseType);
                                });
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private <T> ApiResponse<T> convertResponse(ApiResponse<?> response, Class<T> targetType) {
        try {
            // Tentativa simples de conversão - em um cenário real, seria mais sofisticada
            T convertedBody = targetType.cast(response.getBody());
            return new ApiResponse<>(response.getStatusCode(), response.getHeaders(), convertedBody);
        } catch (ClassCastException e) {
            logger.warn("Não foi possível converter resposta para {}, retornando como está", targetType.getName());
            return (ApiResponse<T>) response;
        }
    }
}

