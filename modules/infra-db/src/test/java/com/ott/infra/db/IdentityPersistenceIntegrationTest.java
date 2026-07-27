package com.ott.infra.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ott.domain.common.Status;
import com.ott.domain.member.domain.Member;
import com.ott.domain.member.domain.Provider;
import com.ott.domain.member.domain.Role;
import com.ott.domain.member_radar_preference.domain.MemberRadarPreference;
import com.ott.infra.db.config.InfraDbConfiguration;
import com.ott.infra.db.member.repository.MemberRepository;
import com.ott.infra.db.member_radar_preference.repository.MemberRadarPreferenceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = InfraDbConfiguration.class)
class IdentityPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberRadarPreferenceRepository memberRadarPreferenceRepository;

    @Test
    void memberRepositoryPersistsAndReadsActiveMembers() {
        // Given
        Member member = memberRepository.saveAndFlush(
                member("identity-reader", "reader@example.com", Role.MEMBER)
        );
        entityManager.clear();

        // When
        Member byProvider = memberRepository.findByProviderAndProviderId(Provider.KAKAO, "identity-reader")
                .orElseThrow();
        Member byActiveId = memberRepository.findByIdAndStatus(member.getId(), Status.ACTIVE)
                .orElseThrow();
        Page<Member> memberList = memberRepository.findMemberList(
                PageRequest.of(0, 10),
                "reader",
                Role.MEMBER
        );

        // Then
        assertThat(byProvider.getEmail()).isEqualTo("reader@example.com");
        assertThat(byActiveId.getId()).isEqualTo(member.getId());
        assertThat(memberList.getContent())
                .extracting(Member::getProviderId)
                .containsExactly("identity-reader");
    }

    @Test
    void radarPreferenceKeepsOneRowPerMember() {
        // Given
        Member member = memberRepository.saveAndFlush(
                member("radar-owner", "radar@example.com", Role.MEMBER)
        );
        memberRadarPreferenceRepository.saveAndFlush(MemberRadarPreference.createDefault(member));

        // When / Then
        assertThatThrownBy(() -> memberRadarPreferenceRepository.save(MemberRadarPreference.createDefault(member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Member member(String providerId, String email, Role role) {
        return Member.builder()
                .provider(Provider.KAKAO)
                .providerId(providerId)
                .email(email)
                .nickname(providerId)
                .role(role)
                .build();
    }
}
