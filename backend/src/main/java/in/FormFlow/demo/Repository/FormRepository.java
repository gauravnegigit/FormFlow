package in.FormFlow.demo.Repository;

import in.FormFlow.demo.Entity.Form;
import in.FormFlow.demo.enums.FormStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormRepository extends JpaRepository<Form, UUID> {

    Page<Form> findAllByOrganization_Id(UUID organizationId, Pageable pageable);

    Page<Form> findAllByOrganization_IdAndStatus(UUID organizationId, FormStatus status, Pageable pageable);

    Optional<Form> findByIdAndOrganization_Id(UUID formId, UUID organizationId);

    Optional<Form> findByPublicSlugAndStatus(String publicSlug, FormStatus status);
}
