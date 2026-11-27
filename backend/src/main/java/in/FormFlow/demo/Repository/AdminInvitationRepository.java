package in.FormFlow.demo.Repository;

import in.FormFlow.demo.Entity.AdminInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, UUID> {

    Optional<AdminInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdminInvitation a JOIN FETCH a.organization WHERE a.tokenHash = :tokenHash")
    Optional<AdminInvitation> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE AdminInvitation a
               SET a.usedAt = :usedAt
             WHERE a.id = :id
               AND a.usedAt IS NULL
               AND a.expiresAt > :now
            """)
    int markUsedIfAvailable(
            @Param("id") UUID id,
            @Param("usedAt") LocalDateTime usedAt,
            @Param("now") LocalDateTime now
    );
}
