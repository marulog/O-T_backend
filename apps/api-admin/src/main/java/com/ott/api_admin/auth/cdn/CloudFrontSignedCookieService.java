package com.ott.api_admin.auth.cdn;

import com.ott.common.security.util.CookieUtil;
import com.ott.common.core.error.BusinessException;
import com.ott.common.core.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloudFrontSignedCookieService {

    private static final String POLICY_COOKIE = "CloudFront-Policy";
    private static final String SIGNATURE_COOKIE = "CloudFront-Signature";
    private static final String KEY_PAIR_ID_COOKIE = "CloudFront-Key-Pair-Id";

    private final CookieUtil cookieUtil;

    @Value("${cloudfront.signed-cookie.resource-url:https://cdn.openthetaste.cloud/contents/*}")
    private String resourceUrl;

    @Value("${cloudfront.signed-cookie.key-pair-id:}")
    private String keyPairId;

    @Value("${cloudfront.signed-cookie.private-key-base64:}")
    private String privateKeyBase64;

    @Value("${cloudfront.signed-cookie.ttl-seconds:}")
    private int ttlMillis;

    public void addSignedCookies(HttpServletResponse response) {

        if (resourceUrl == null || resourceUrl.isBlank()
                || keyPairId == null || keyPairId.isBlank()
                || privateKeyBase64 == null || privateKeyBase64.isBlank()
                || ttlMillis <= 0) {
            throw new BusinessException(ErrorCode.CLOUDFRONT_SIGNED_COOKIE_CONFIG_INVALID);
        }

        try {
            long ttlInSeconds = ttlMillis / 1000L;
            long expireEpoch = Instant.now().plusSeconds(ttlInSeconds).getEpochSecond();
            String policy = buildPolicy(resourceUrl, expireEpoch);

            PrivateKey privateKey;
            try {
                privateKey = loadPrivateKey(privateKeyBase64);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.CLOUDFRONT_PRIVATE_KEY_INVALID);
            }

            byte[] signatureBytes;
            try {
                signatureBytes = sign(policy, privateKey);
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.CLOUDFRONT_POLICY_SIGN_FAILED);
            }

            cookieUtil.addCookie(response, POLICY_COOKIE, cloudFrontBase64(policy.getBytes(StandardCharsets.UTF_8)), ttlMillis);
            cookieUtil.addCookie(response, SIGNATURE_COOKIE, cloudFrontBase64(signatureBytes), ttlMillis);
            cookieUtil.addCookie(response, KEY_PAIR_ID_COOKIE, keyPairId, ttlMillis);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.CLOUDFRONT_SIGNED_COOKIE_ISSUE_FAILED);
        }
    }

    public void clearSignedCookies(HttpServletResponse response) {

        cookieUtil.deleteCookie(response, POLICY_COOKIE);
        cookieUtil.deleteCookie(response, SIGNATURE_COOKIE);
        cookieUtil.deleteCookie(response, KEY_PAIR_ID_COOKIE);
    }

    private String buildPolicy(String resource, long expireEpoch) {
        return "{\"Statement\":[{\"Resource\":\"" + resource + "\",\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":" + expireEpoch + "}}}]}";
    }

    private byte[] sign(String policy, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(privateKey);
        signer.update(policy.getBytes(StandardCharsets.UTF_8));
        return signer.sign();
    }

    private PrivateKey loadPrivateKey(String pemBase64) throws Exception {
        String pem = new String(Base64.getDecoder().decode(pemBase64), StandardCharsets.UTF_8);
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private String cloudFrontBase64(byte[] raw) {
        return Base64.getEncoder().encodeToString(raw)
                .replace('+', '-')
                .replace('=', '_')
                .replace('/', '~');
    }
}
