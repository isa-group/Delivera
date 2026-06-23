package com.delivera.client.config.properties;


import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "delivera")
public class DeliveraProperties {

    private Client client = new Client();
    private Security security = new Security();

    @Getter
    @Setter
    public static class Client {

        private String serviceName;

        private List<String> internalHosts;

        private InternalAuth internalAuth = new InternalAuth();
    }

    @Getter
    @Setter
    public static class InternalAuth {

        private boolean enabled = true;

        private String headerName = "X-Internal-Key";

        private String serviceHeaderName = "X-Service-Name";
    }

    @Getter
    @Setter
    public static class Security {

        private Map<String, String> services;
    }


}
