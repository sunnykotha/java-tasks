package com.priorauth.workflow;

import java.time.Instant;

/** Core authorization-request contract used by the workflow. */
public interface AuthRequest {
    String getId();
    String getProcedureCode();
    RequestPriority getPriority();
    Instant getSubmittedAt();
    boolean isDocumentationComplete();
}
