package com.priorauth.workflow;

import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AuthorizationRequest implements AuthRequest {
    private final String id;
    private final String procedureCode;
    private final RequestPriority priority;
    private final Instant submittedAt;
    private final Set<String> requiredDocuments;
    private final Set<String> receivedDocuments = new HashSet<>();
    private final Map<String, ClinicalDocument> clinicalDocuments = new HashMap<>();
    private final AutoApprovalTerms autoApprovalTerms;

    public AuthorizationRequest(
            String id,
            String procedureCode,
            RequestPriority priority,
            Instant submittedAt,
            Set<String> requiredDocuments) {
        this(id, procedureCode, priority, submittedAt, requiredDocuments, null);
    }

    public AuthorizationRequest(
            String id,
            String procedureCode,
            RequestPriority priority,
            Instant submittedAt,
            Set<String> requiredDocuments,
            AutoApprovalTerms autoApprovalTerms) {

        this.id = requireText(id, "Request ID");
        this.procedureCode = requireText(
                procedureCode, "Procedure code");
        this.priority = Objects.requireNonNull(
                priority, "Priority is required");
        this.submittedAt = Objects.requireNonNull(
                submittedAt, "Submission time is required");
        Objects.requireNonNull(
                requiredDocuments, "Required documents are required");

        this.requiredDocuments = new HashSet<>();
        for (String document : requiredDocuments) {
            this.requiredDocuments.add(
                    requireText(document, "Required document"));
        }
        this.autoApprovalTerms = autoApprovalTerms;
    }

    public synchronized void receiveDocument(String document) {
        String documentType = requireText(document, "Document");
        receivedDocuments.add(documentType);
        clinicalDocuments.putIfAbsent(documentType,
                new ClinicalDocument(documentType, null, null));
    }

    public synchronized void receiveDocument(ClinicalDocument document) {
        Objects.requireNonNull(document, "Clinical document is required");
        receivedDocuments.add(document.documentType());
        clinicalDocuments.put(document.documentType(), document);
    }

    public synchronized Map<String, ClinicalDocument> getClinicalDocuments() {
        return Map.copyOf(clinicalDocuments);
    }

    public synchronized boolean isDocumentationComplete() {
        return receivedDocuments.containsAll(requiredDocuments);
    }

    public boolean hasAutoApprovalTerms() {
        return autoApprovalTerms != null;
    }

    public AutoApprovalTerms getAutoApprovalTerms() {
        if (autoApprovalTerms == null) {
            throw new IllegalStateException(
                    "Automatic approval terms have not been configured");
        }
        return autoApprovalTerms;
    }

    public String getId() {
        return id;
    }

    public String getProcedureCode() {
        return procedureCode;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Set<String> getRequiredDocuments() {
        return Set.copyOf(requiredDocuments);
    }

    public synchronized Set<String> getReceivedDocuments() {
        return Set.copyOf(receivedDocuments);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank");
        }
        return value.trim();
    }
}