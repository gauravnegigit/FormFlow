package in.FormFlow.demo.Repository;

import in.FormFlow.demo.Entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findAllByForm_IdAndOrganization_Id(UUID formId, UUID organizationId);

    Optional<Submission> findByIdAndOrganization_Id(UUID submissionId, UUID organizationId);

    List<Submission> findAllByAssignedReviewer_IdAndOrganization_Id(UUID reviewerId, UUID organizationId);

    Optional<Submission> findByIdAndAssignedReviewer_IdAndOrganization_Id(
            UUID submissionId,
            UUID reviewerId,
            UUID organizationId
    );

    Optional<Submission> findByTrackingToken(String trackingToken);
}
