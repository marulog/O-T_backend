package com.ott.domain.watch_history.domain;

import com.ott.domain.common.BaseEntity;
import com.ott.domain.contents.domain.Contents;
import com.ott.domain.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Builder
@Getter
@Table(
    name = "watch_history",
    uniqueConstraints  = {
        @UniqueConstraint(
            name = "uk_watch_history_member_contents",
            columnNames = {"member_id", "contents_id"}
        )
    }
)
public class WatchHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contents_id", nullable = false)
    private Contents contents;

    @Column(name = "last_watched_at")
    private LocalDateTime lastWatchedAt;

    @Column(name = "re_watch_count", nullable = false)
    private Integer reWatchCount;

    @Column(name = "is_used_for_ml", nullable = false)
    private boolean isUsedForMl;

    public void updateLastWatchedAt(){
        this.lastWatchedAt = LocalDateTime.now();
    }

    public void markAsUsedForMl() {
        this.isUsedForMl = true;
    }
}
