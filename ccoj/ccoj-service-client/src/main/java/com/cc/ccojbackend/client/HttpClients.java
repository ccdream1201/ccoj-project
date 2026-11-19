package com.cc.ccojbackend.client;

import com.cc.ccojbackend.common.constant.InternalAccess;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

public final class HttpClients {
    private HttpClients() {}

    public static <T> T bind(RestClient.Builder builder, String baseUrl, Class<T> contract) {
        var client = builder.baseUrl(baseUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build().createClient(contract);
    }

    public static RestClient.Builder internalBuilder(String key) {
        return builder(Duration.ofSeconds(5)).defaultHeader(InternalAccess.HEADER, key);
    }

    public static RestClient.Builder builder(Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }
}
