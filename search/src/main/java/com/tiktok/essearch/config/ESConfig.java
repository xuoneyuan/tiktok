package com.tiktok.essearch.config;

import co.elastic.clients.elasticsearch.transform.ElasticsearchTransformClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import co.elastic.clients.elasticsearch.ElasticsearchClient;

import java.io.IOException;

@Configuration
@Slf4j
public class ESConfig {

    @Value("${es.url}")
    String esUrl;

    @Value("${es.port}")
    int esPort;

    private org.elasticsearch.client.RestClient restClient;
    private ElasticsearchClient client;

    private ElasticsearchTransport transport;

    @Bean(name = "elasticsearchClient")
    public ElasticsearchClient getElasticsearchClient() {
        restClient = RestClient.builder(
                new HttpHost(esUrl, esPort)
        ).build();
        // 使用Jackson映射器创建传输层
        transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper()
        );
        // 创建API客户端
        client = new ElasticsearchClient(transport);
        return client;
    }


    public void close() {
        if (client != null) {
            try {
                transport.close();
                restClient.close();
            } catch (IOException e) {
                log.error("关闭es连接异常");
            }
        }
    }
}