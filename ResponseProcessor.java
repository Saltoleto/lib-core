package com.sequentialapi.service;

import com.sequentialapi.model.ApiResponse;

/**
 * Interface para processamento de respostas de API.
 * Segue o princípio Aberto/Fechado (OCP) do SOLID - aberto para extensão, fechado para modificação.
 */
public interface ResponseProcessor<T, R> {
    
    /**
     * Processa uma resposta de API e extrai dados relevantes.
     * 
     * @param response A resposta da API a ser processada
     * @return Os dados processados
     */
    R process(ApiResponse<T> response);
    
    /**
     * Verifica se o processador pode lidar com o tipo de resposta fornecido.
     * 
     * @param responseType O tipo da resposta
     * @return true se pode processar, false caso contrário
     */
    boolean canProcess(Class<?> responseType);
}

