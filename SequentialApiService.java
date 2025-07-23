package com.sequentialapi.service;

import com.sequentialapi.model.ApiRequest;
import com.sequentialapi.model.ApiResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Interface para execução sequencial de chamadas de API.
 * Segue o princípio da Responsabilidade Única (SRP) do SOLID.
 */
public interface SequentialApiService {
    
    /**
     * Executa uma sequência de chamadas de API onde cada chamada depende do resultado da anterior.
     * 
     * @param initialRequest A primeira requisição da sequência
     * @param requestBuilders Lista de funções que constroem as próximas requisições baseadas na resposta anterior
     * @param <T> Tipo do resultado final
     * @return CompletableFuture com o resultado da última chamada
     */
    <T> CompletableFuture<ApiResponse<T>> executeSequentialCalls(
        ApiRequest initialRequest,
        List<Function<ApiResponse<?>, ApiRequest>> requestBuilders,
        Class<T> finalResponseType
    );
    
    /**
     * Executa uma única chamada de API.
     * 
     * @param request A requisição a ser executada
     * @param responseType Tipo esperado da resposta
     * @param <T> Tipo da resposta
     * @return CompletableFuture com a resposta da API
     */
    <T> CompletableFuture<ApiResponse<T>> executeSingleCall(ApiRequest request, Class<T> responseType);
}

