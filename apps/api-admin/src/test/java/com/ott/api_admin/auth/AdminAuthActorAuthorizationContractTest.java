package com.ott.api_admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.ott.api_admin.auth.cdn.CloudFrontSignedCookieService;
import com.ott.api_admin.auth.controller.AdminAuthController;
import com.ott.api_admin.auth.service.AdminAuthService;
import com.ott.common.security.util.CookieUtil;
import com.ott.domain.member.domain.Role;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminAuthActorAuthorizationContractTest {

    private static final Long PRINCIPAL_ID = 11L;

    @Mock
    private AdminAuthService adminAuthService;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private CloudFrontSignedCookieService cloudFrontSignedCookieService;

    @ParameterizedTest(name = "{0} logout extracts the authenticated principal")
    @MethodSource("actors")
    void logoutExtractsPrincipalAndClearsAdminCookies(String label, Authentication authentication) {
        AdminAuthController controller = new AdminAuthController(
                adminAuthService,
                cookieUtil,
                cloudFrontSignedCookieService);
        ReflectionTestUtils.setField(controller, "accessCookieName", "adminAccessToken");
        ReflectionTestUtils.setField(controller, "refreshCookieName", "adminRefreshToken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        var actual = controller.logout(authentication, response);

        assertThat(actual.getStatusCode().value()).isEqualTo(204);
        verify(adminAuthService).logout(PRINCIPAL_ID);
        verify(cookieUtil).deleteCookie(response, "adminAccessToken");
        verify(cookieUtil).deleteCookie(response, "adminRefreshToken");
        verify(cloudFrontSignedCookieService).clearSignedCookies(response);
    }

    private static Stream<Arguments> actors() {
        return Stream.of(
                Arguments.of("ADMIN", authentication(PRINCIPAL_ID, Role.ADMIN)),
                Arguments.of("EDITOR", authentication(PRINCIPAL_ID, Role.EDITOR))
        );
    }

    private static Authentication authentication(Long principal, Role role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role.getKey())));
    }
}
