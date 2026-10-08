package me.yuugao.pullhire.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RSA-2048 key material for JWT RS256 and the JWKS document
 * ({@code docs/architecture.md §6.1}).
 *
 * <p>Keys are read from {@code JWT_PRIVATE_KEY} / {@code JWT_PUBLIC_KEY} as PKCS#8
 * (private) and X.509 SubjectPublicKeyInfo (public), either PEM or raw base64 DER.
 * When they are absent or unparseable an ephemeral keypair is generated at startup
 * and a WARNING is logged — a development-only fallback. Keys are never committed.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);
    private static final int KEY_SIZE = 2048;
    private static final String KEY_ID_ALGORITHM = "SHA-256";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    @Autowired
    public RsaKeyProvider(Environment environment) {
        this(environment.getProperty("JWT_PRIVATE_KEY"), environment.getProperty("JWT_PUBLIC_KEY"));
    }

    public RsaKeyProvider(String privateKeyValue, String publicKeyValue) {
        KeyPair pair = resolveKeyPair(privateKeyValue, publicKeyValue);
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
        this.keyId = computeKeyId(this.publicKey);
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    /** RFC 7517 JWKS document for {@code GET /.well-known/jwks.json}. */
    public Map<String, Object> jwks() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", base64UrlUnsigned(publicKey.getModulus()));
        jwk.put("e", base64UrlUnsigned(publicKey.getPublicExponent()));
        return Map.of("keys", List.of(jwk));
    }

    private static KeyPair resolveKeyPair(String privateKeyValue, String publicKeyValue) {
        RSAPrivateKey privateKey = parsePrivateKey(privateKeyValue);
        RSAPublicKey publicKey = parsePublicKey(publicKeyValue);

        if (privateKey instanceof RSAPrivateCrtKey crt && publicKey == null) {
            publicKey = derivePublicKey(crt);
        }
        if (privateKey != null && publicKey != null) {
            return new KeyPair(publicKey, privateKey);
        }

        log.warn("JWT_PRIVATE_KEY/JWT_PUBLIC_KEY отсутствуют или неразборчивы — "
                + "сгенерирована эфемерная RSA-{} пара (только для dev; в продакшене задайте ключи)", KEY_SIZE);
        return generateKeyPair();
    }

    private static RSAPrivateKey parsePrivateKey(String value) {
        byte[] der = decode(value);
        if (der == null) {
            return null;
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            log.debug("Не удалось разобрать JWT_PRIVATE_KEY: {}", ex.getMessage());
            return null;
        }
    }

    private static RSAPublicKey parsePublicKey(String value) {
        byte[] der = decode(value);
        if (der == null) {
            return null;
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            log.debug("Не удалось разобрать JWT_PUBLIC_KEY: {}", ex.getMessage());
            return null;
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey privateKey) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (Exception ex) {
            log.debug("Не удалось вывести публичный ключ из приватного: {}", ex.getMessage());
            return null;
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE, new SecureRandom());
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA недоступен в JVM", ex);
        }
    }

    /** Decodes PEM or raw base64 (standard or URL alphabet); {@code null} when unusable. */
    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace("\\n", "\n").trim();
        if (normalized.contains("-----BEGIN")) {
            normalized = normalized
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s", "");
        } else {
            normalized = normalized.replaceAll("\\s", "");
        }
        if (normalized.isEmpty()) {
            return null;
        }
        for (Base64.Decoder decoder : List.of(Base64.getDecoder(), Base64.getMimeDecoder(), Base64.getUrlDecoder())) {
            try {
                return decoder.decode(normalized);
            } catch (IllegalArgumentException ignored) {
                // try the next alphabet
            }
        }
        return null;
    }

    private static String computeKeyId(RSAPublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance(KEY_ID_ALGORITHM).digest(publicKey.getEncoded());
            byte[] truncated = Arrays.copyOf(digest, 16);
            return base64Url(truncated);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(KEY_ID_ALGORITHM + " недоступен в JVM", ex);
        }
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int start = 0;
        while (start < bytes.length - 1 && bytes[start] == 0) {
            start++;
        }
        return base64Url(Arrays.copyOfRange(bytes, start, bytes.length));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
