package in.FormFlow.demo.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectSubmissionRequest(

        @NotBlank(message = "Rejection reason is required")
        @Size(max = 2000, message = "Rejection reason cannot exceed 2000 characters")
        String rejectionReason

) {}
