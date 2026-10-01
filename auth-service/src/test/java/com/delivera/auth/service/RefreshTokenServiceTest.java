package com.delivera.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import com.delivera.auth.dto.Device;
import com.delivera.auth.exception.InvalidRefreshActionException;
import com.delivera.auth.exception.InvalidRefreshTokenException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.model.RefreshToken;
import com.delivera.auth.repository.RefreshTokenRepository;

@ExtendWith (MockitoExtension.class)
class RefreshTokenServiceTest {

        @Mock 
        private RefreshTokenRepository repository;

        @InjectMocks 
        private RefreshTokenService service;

        private Credential credential;
        private UUID userId;
        private UUID tokenId;

        @BeforeEach 
        void setup() {

                ReflectionTestUtils.setField(
                        service,
                        "daysToRefresh",
                        4
                );

                ReflectionTestUtils.setField(
                        service,
                        "maxDaysToRefresh",
                        30
                );

                userId = UUID.randomUUID();
                tokenId = UUID.randomUUID();

                credential = new Credential();
                credential.setUserId(userId);
        }

        private String buildToken(
                UUID tokenId,
                String secret
        ) {
                return tokenId + "." + secret;
        }

        private static String hash(String token) throws Exception {

                MessageDigest digest =
                        MessageDigest.getInstance("SHA-256");

                byte[] hash =
                        digest.digest(
                                token.getBytes(StandardCharsets.UTF_8)
                        );

                return Base64.getEncoder()
                        .encodeToString(hash);
        }
        @Test
        void removeExpiredTokens_shouldDeleteExpiredTokens() {

                service.removeExpiredTokens();

                verify(repository)
                        .deleteByExpiredTokens(any());
        }
        @Test
        void create_shouldCreateRefreshToken() {

                String token =
                        service.create(
                                credential,
                                "device",
                                "agent",
                                "127.0.0.1",
                                UUID.randomUUID()
                        );

                assertThat(token)
                        .contains(".");

                verify(repository)
                        .save(any(RefreshToken.class));
        }
        @Test
        void validateAndGet_shouldThrowWhenTokenIsNull() {

                assertThatThrownBy(
                        () -> service.validateAndGet(null)
                ).isInstanceOf(
                        InvalidRefreshTokenException.class
                );
        }
        @Test
        void validateAndGet_shouldThrowWhenFormatIsInvalid() {
        
                assertThatThrownBy(
                        () -> service.validateAndGet(
                                "invalid-token"
                        )
                ).isInstanceOf(
                        InvalidRefreshTokenException.class
                );
        }
        @Test
        void validateAndGet_shouldThrowWhenUuidIsInvalid() {
        
                assertThatThrownBy(
                        () -> service.validateAndGet(
                                "not-an-uuid.secret"
                        )
                ).isInstanceOf(
                        InvalidRefreshTokenException.class
                );
        }
        @Test
        void validateAndGet_shouldThrowWhenTokenDoesNotExist() {

                when(
                        repository.findValidById(tokenId)
                ).thenReturn(Optional.empty());

                String token =
                        buildToken(
                                tokenId,
                                "secret"
                        );

                assertThatThrownBy(
                        () -> service.validateAndGet(token)
                ).isInstanceOf(
                        InvalidRefreshTokenException.class
                );
        }
        @Test
        void validateAndGet_shouldThrowWhenSecretIsInvalid()
        throws Exception {

                RefreshToken refreshToken =
                        new RefreshToken();

                refreshToken.setSecretHash(
                        hash("correct-secret")
                );

                refreshToken.setExpiredAt(
                        Instant.now().plusSeconds(1000)
                );

                refreshToken.setMaxExpiredAt(
                        Instant.now().plusSeconds(1000)
                );

                when(
                        repository.findValidById(tokenId)
                ).thenReturn(Optional.of(refreshToken));

                String token =
                        buildToken(
                                tokenId,
                                "wrong-secret"
                        );

                assertThatThrownBy(
                        () -> service.validateAndGet(token)
                ).isInstanceOf(
                        InvalidRefreshTokenException.class
                );
        }
        @Test
        void validateAndGet_shouldDeleteExpiredToken()
        throws Exception {
        
                RefreshToken refreshToken =
                        new RefreshToken();
        
                refreshToken.setSecretHash(
                        hash("secret")
                );
        
                refreshToken.setExpiredAt(
                        Instant.now().minusSeconds(60)
                );
        
                refreshToken.setMaxExpiredAt(
                        Instant.now().plusSeconds(100)
                );
        
                when(
                        repository.findValidById(tokenId)
                ).thenReturn(Optional.of(refreshToken));
        
                String token =
                        buildToken(
                                tokenId,
                                "secret"
                        );
        
                assertThatThrownBy(
                        () -> service.validateAndGet(token)
                ).isInstanceOf(
                        InvalidRefreshTokenException.class
                );
        
                verify(repository)
                        .delete(refreshToken);
        }
        @Test
        void validateAndGet_shouldReturnToken()
        throws Exception {
        
                RefreshToken refreshToken =
                        new RefreshToken();
        
                refreshToken.setSecretHash(
                        hash("secret")
                );
        
                refreshToken.setExpiredAt(
                        Instant.now().plusSeconds(1000)
                );
        
                refreshToken.setMaxExpiredAt(
                        Instant.now().plusSeconds(1000)
                );
        
                when(
                        repository.findValidById(tokenId)
                ).thenReturn(Optional.of(refreshToken));
        
                RefreshToken result =
                        service.validateAndGet(
                                buildToken(
                                        tokenId,
                                        "secret"
                                )
                        );
        
                assertThat(result)
                        .isSameAs(refreshToken);
        }
        @Test
        void use_shouldUpdateToken() {
        
                RefreshToken token =
                        new RefreshToken();
        
                token.setDevice("device");
                token.setUserAgent("agent");
                token.setSuspicious(false);
        
                when(repository.save(token))
                        .thenReturn(token);
        
                service.use(
                        token,
                        "device",
                        "agent",
                        "ip",
                        UUID.randomUUID()
                );
        
                verify(repository)
                        .save(token);
        }
        @Test
        void use_shouldMarkAsSuspicious() {
        
                RefreshToken token =
                        new RefreshToken();
        
                token.setDevice("old-device");
                token.setUserAgent("old-agent");
                token.setSuspicious(false);
        
                when(repository.save(token))
                        .thenReturn(token);
        
                service.use(
                        token,
                        "new-device",
                        "new-agent",
                        "ip",
                        UUID.randomUUID()
                );
        
                assertThat(token.getSuspicious())
                        .isTrue();
        }
        @Test
        void refresh_shouldThrowInvalidRefreshAction()
        throws Exception {

                RefreshToken refreshToken =
                        new RefreshToken();
                
                Credential credential = new Credential();

                refreshToken.setCredential(credential);

                refreshToken.setDevice("device");
                refreshToken.setUserAgent("agent");

                refreshToken.setCompanyId(
                        UUID.randomUUID()
                );

                refreshToken.setSecretHash(
                        hash("secret")
                );

                refreshToken.setExpiredAt(
                        Instant.now().plusSeconds(1000)
                );

                refreshToken.setMaxExpiredAt(
                        Instant.now().plusSeconds(1000)
                );

                when(
                        repository.findValidById(tokenId)
                ).thenReturn(Optional.of(refreshToken));

                doThrow(
                        new OptimisticLockingFailureException("lock")
                ).when(repository)
                .deleteTokenById(any(), any());

                assertThatThrownBy(
                        () -> service.refresh(
                                buildToken(tokenId, "secret"),
                                "device",
                                "agent",
                                "ip"
                        )
                ).isInstanceOf(
                        InvalidRefreshActionException.class
                );
        }
        @Test
        void removeToken_shouldDeleteRefreshToken()
        throws Exception {
        
                RefreshToken refreshToken =
                        new RefreshToken();
        
                refreshToken.setSecretHash(
                        hash("secret")
                );
        
                refreshToken.setExpiredAt(
                        Instant.now().plusSeconds(1000)
                );
        
                refreshToken.setMaxExpiredAt(
                        Instant.now().plusSeconds(1000)
                );
        
                when(
                        repository.findValidById(tokenId)
                ).thenReturn(Optional.of(refreshToken));
        
                service.removeToken(
                        buildToken(
                                tokenId,
                                "secret"
                        )
                );
        
                verify(repository)
                        .delete(refreshToken);
        }
        @Test
        void revokeOthersTokens_shouldDelegate() {
        
                RefreshToken refreshToken =
                        new RefreshToken();
        
                refreshToken.setId(tokenId);
                refreshToken.setCredential(credential);
        
                service.revokeOthersTokens(refreshToken);
        
                verify(repository)
                        .revokeOthersTokens(
                                tokenId,
                                credential
                        );
        }
        @Test
        void notSuspicious_shouldClearFlag() {
        
                RefreshToken refreshToken =
                        new RefreshToken();
        
                refreshToken.setSuspicious(true);
        
                service.notSuspicious(refreshToken);
        
                assertThat(refreshToken.getSuspicious())
                        .isFalse();
        
                verify(repository)
                        .save(refreshToken);
        }
        @Test
        void getDevices_shouldReturnDevices() {

                UUID sessionId =
                        UUID.randomUUID();

                List<Device> devices =
                        List.of();

                when(
                        repository.getDevices(
                                userId,
                                sessionId
                        )
                ).thenReturn(devices);

                List<Device> result =
                        service.getDevices(
                                userId,
                                sessionId
                        );

                assertThat(result)
                        .isSameAs(devices);
        }
        @Test
        void use_shouldNotMarkAsSuspiciousWhenDeviceMatches() {

                RefreshToken token =
                        new RefreshToken();

                token.setDevice("device");
                token.setUserAgent("agent");
                token.setSuspicious(false);

                when(repository.save(token))
                        .thenReturn(token);

                service.use(
                        token,
                        "device",
                        "agent",
                        "ip",
                        UUID.randomUUID()
                );

                assertThat(token.getSuspicious())
                        .isFalse();
        }
        @Test
        void use_shouldRemainSuspiciousWhenAlreadyMarked() {

                RefreshToken token =
                        new RefreshToken();

                token.setDevice("device");
                token.setUserAgent("agent");
                token.setSuspicious(true);

                when(repository.save(token))
                        .thenReturn(token);

                service.use(
                        token,
                        "device",
                        "agent",
                        "ip",
                        UUID.randomUUID()
                );

                assertThat(token.getSuspicious())
                        .isTrue();
        }
        @Test
        void removeOthersTokens_shouldDelegateToRepository() {

                RefreshToken refreshToken =
                        new RefreshToken();

                refreshToken.setId(tokenId);
                refreshToken.setCredential(credential);

                service.removeOthersTokens(refreshToken);

                verify(repository)
                        .deleteOthersTokens(
                                tokenId,
                                credential
                        );
        }

}
