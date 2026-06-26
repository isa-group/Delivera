package com.delivera.auth.security.jwt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class KeyLoader {

    public PrivateKey loadPrivateKey(String path) throws Exception {
        String key = loadFile(path);

        key = key.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);

        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    public PublicKey loadPublicKey(String path) throws Exception {
        String key = loadFile(path);

        key = key.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(key);

        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }

    private String loadFile(String path) throws Exception {

        
        // ✅ file: → filesystem
        if (path.startsWith("file:")) {
            return new String(Files.readAllBytes(Path.of(path.substring(5))));
        }

        // ✅ ruta absoluta (/app/...)
        if (path.startsWith("/")) {
            return new String(Files.readAllBytes(Path.of(path)));
        }


        //  Develop --> classpath
        var resource = new ClassPathResource(path);
        return new String(resource.getInputStream().readAllBytes());
    }
}

