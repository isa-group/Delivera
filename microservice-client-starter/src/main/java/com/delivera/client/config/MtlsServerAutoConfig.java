package com.delivera.client.config;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.delivera.client.config.properties.MtlsServerProperties;

@Configuration
@EnableConfigurationProperties(MtlsServerProperties.class)
@ConditionalOnProperty(
    prefix = "delivera.mtls.server",
    name = "enabled",
    havingValue = "true"
)
public class MtlsServerAutoConfig implements
        WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final MtlsServerProperties config;

    public MtlsServerAutoConfig(MtlsServerProperties config) {
        this.config = config;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addAdditionalTomcatConnectors(createMtlsConnector(this.config));
    }


    

    private Connector createMtlsConnector(MtlsServerProperties config) {

        Connector connector = new Connector(Http11NioProtocol.class.getName());

        connector.setPort(config.getPort());
        connector.setSecure(true);
        connector.setScheme("https");

        Http11NioProtocol protocol =
                (Http11NioProtocol) connector.getProtocolHandler();

        protocol.setSSLEnabled(true);

        SSLHostConfig sslHostConfig = new SSLHostConfig();


        sslHostConfig.setCertificateVerification("required");

   
        SSLHostConfigCertificate certificate =
                new SSLHostConfigCertificate(
                        sslHostConfig,
                        SSLHostConfigCertificate.Type.RSA);

        certificate.setCertificateKeystoreFile(resolvePath(config.getKeyStore(), config.isUseTempFile()));
        certificate.setCertificateKeystorePassword(config.getKeyStorePassword());
        certificate.setCertificateKeystoreType(config.getKeyStoreType());

        sslHostConfig.addCertificate(certificate);

        sslHostConfig.setTruststoreFile(resolvePath(config.getTrustStore(),config.isUseTempFile()));
        sslHostConfig.setTruststorePassword(config.getTrustStorePassword());
        

        connector.addSslHostConfig(sslHostConfig);

        return connector;
    }

   
    private String resolvePath(String location, Boolean useTempFile) {

        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }

        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            Resource resource = resourceLoader.getResource(location);

            if ("file".equals(resource.getURL().getProtocol())) {
                return resource.getFile().getAbsolutePath();
            }

            if (!useTempFile && location.startsWith("classpath:") ) {
                try {
                    return new ClassPathResource(
                            location.replace("classpath:", "")
                    ).getFile().getAbsolutePath();
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Cannot resolve classpath  resource in runtime (use file: in prod) and useTempFile=false: " + location, e);
                }
            }


            File tempFile = File.createTempFile("cert-", ".tmp");
            tempFile.deleteOnExit();

            try (InputStream is = resource.getInputStream()) {
                Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return tempFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("Error resolving resource: " + location, e);
        }
    }

}
