package com.alexaf.gitlabmcp.config;

import com.alexaf.gitlabmcp.gitlab.client.GitlabProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;

@Configuration
public class GitlabMcpConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Bean
    RestClient.Builder restClientBuilder(
            GitlabProperties properties,
            ObjectProvider<SslBundles> sslBundlesProvider
    ) {
        HttpClient.Builder httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (StringUtils.hasText(properties.proxyUrl())) {
            URI proxy = URI.create(properties.proxyUrl().trim());
            int port = proxy.getPort() >= 0
                    ? proxy.getPort()
                    : defaultPort(proxy.getScheme());
            httpClient.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), port)));
        }
        if (StringUtils.hasText(properties.sslBundle())) {
            SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
            if (sslBundles == null) {
                throw new IllegalArgumentException(
                        "GITLAB_SSL_BUNDLE is set but no Spring SSL bundles are configured");
            }
            httpClient.sslContext(sslBundles.getBundle(properties.sslBundle().trim()).createSslContext());
        }
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient.build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
