package in.FormFlow.demo.Repository;

import in.FormFlow.demo.Entity.Submission;
import in.FormFlow.demo.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Page<Submission> findAllByForm_IdAndOrganization_Id(UUID formId, UUID organizationId, Pageable pageable);

    Page<Submission> findAllByForm_IdAndOrganization_IdAndStatus(
            UUID formId,
            UUID organizationId,
            SubmissionStatus status,
            Pageable pageable
    );

    Optional<Submission> findByIdAndOrganization_Id(UUID submissionId, UUID organizationId);

    Page<Submission> findAllByAssignedReviewer_IdAndOrganization_Id(
            UUID reviewerId,
            UUID organizationId,
            Pageable pageable
    );

    Page<Submission> findAllByAssignedReviewer_IdAndOrganization_IdAndStatus(
            UUID reviewerId,
            UUID organizationId,
            SubmissionStatus status,
            Pageable pageable
    );

    Optional<Submission> findByIdAndAssignedReviewer_IdAndOrganization_Id(
            UUID submissionId,
            UUID reviewerId,
            UUID organizationId
    );

    Optional<Submission> findByTrackingToken(String trackingToken);
}
