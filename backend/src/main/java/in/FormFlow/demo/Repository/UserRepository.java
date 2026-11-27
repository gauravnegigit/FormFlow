package in.FormFlow.demo.Repository;

import in.FormFlow.demo.Entity.User;
import in.FormFlow.demo.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllByOrganization_Id(UUID organizationId);

    List<User> findAllByOrganization_IdAndRole(UUID organizationId, UserRole role);

    Optional<User> findByIdAndOrganization_Id(UUID userId, UUID organizationId);
}
