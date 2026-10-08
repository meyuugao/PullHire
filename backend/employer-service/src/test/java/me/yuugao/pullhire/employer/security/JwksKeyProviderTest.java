package me.yuugao.pullhire.employer.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Finding #9: a {@code kid}-less token is accepted only with exactly one RSA
 * key; with several keys the selection fails closed.
 */
class JwksKeyProviderTest {

    private static RSAPublicKey rsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return (RSAPublicKey) generator.generateKeyPair().getPublic();
    }

    @Test
    void singleKeyWithoutKidIsAccepted() throws Exception {
        RSAPublicKey key = rsaKey();
        List<JwksKeyProvider.JwkKey> keys = List.of(new JwksKeyProvider.JwkKey("k1", key));

        assertThat(JwksKeyProvider.selectable(keys, null)).isTrue();
        assertThat(JwksKeyProvider.select(keys, null)).isSameAs(key);
    }

    @Test
    void multipleKeysWithoutKidAreRejected() throws Exception {
        List<JwksKeyProvider.JwkKey> keys = List.of(
                new JwksKeyProvider.JwkKey("k1", rsaKey()),
                new JwksKeyProvider.JwkKey("k2", rsaKey()));

        assertThat(JwksKeyProvider.selectable(keys, null)).isFalse();
        assertThatThrownBy(() -> JwksKeyProvider.select(keys, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matchingKidIsAccepted() throws Exception {
        RSAPublicKey first = rsaKey();
        RSAPublicKey second = rsaKey();
        List<JwksKeyProvider.JwkKey> keys = List.of(
                new JwksKeyProvider.JwkKey("k1", first),
                new JwksKeyProvider.JwkKey("k2", second));

        assertThat(JwksKeyProvider.select(keys, "k2")).isSameAs(second);
    }

    @Test
    void unknownKidIsRejected() throws Exception {
        List<JwksKeyProvider.JwkKey> keys = List.of(
                new JwksKeyProvider.JwkKey("k1", rsaKey()),
                new JwksKeyProvider.JwkKey("k2", rsaKey()));

        assertThatThrownBy(() -> JwksKeyProvider.select(keys, "unknown"))
                .isInstanceOf(IllegalStateException.class);
    }
}
