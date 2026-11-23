
package in.FormFlow.demo.Repository;

import in.FormFlow.demo.Entity.Form;
import in.FormFlow.demo.enums.FormStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormRepository extends JpaRepository<Form, UUID> {

    // Find all forms created by a particular user
    List<Form> findAllByCreatedBy_Id(UUID userId);

    // Find a particular form belonging to a particular user
    Optional<Form> findByIdAndCreatedBy_Id(UUID formId, UUID userId);

    Optional<Form> findByPublicSlugAndStatus(
        String publicSlug,
        FormStatus status
);

}
