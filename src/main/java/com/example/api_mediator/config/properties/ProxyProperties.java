package com.example.api_mediator.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {
    private List<ProxyApi> apis;

    public static class ProxyApi {
        private String name;
        private String path;
        private String url;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public List<ProxyApi> getApis() {
        return apis;
    }

    public void setApis(List<ProxyApi> apis) {
        this.apis = apis;
    }
}