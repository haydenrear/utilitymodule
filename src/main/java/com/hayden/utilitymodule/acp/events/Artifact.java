package com.hayden.utilitymodule.acp.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.utilitymodule.security.SignatureUtil;
import lombok.Builder;
import lombok.With;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Base interface for all artifacts in the execution tree.
 * 
 * Artifacts are immutable nodes that capture execution state:
 * - prompts, templates, arguments
 * - tool I/O
 * - configuration
 * - outcomes
 * - captured events
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "artifactType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Artifact.ExecutionArtifact.class, name = "Execution"),
    @JsonSubTypes.Type(value = Artifact.ExecutionConfigArtifact.class, name = "ExecutionConfig"),
    @JsonSubTypes.Type(value = Artifact.RenderedPromptArtifact.class, name = "RenderedPrompt"),
    @JsonSubTypes.Type(value = Artifact.PromptArgsArtifact.class, name = "PromptArgs"),
    @JsonSubTypes.Type(value = Artifact.PromptContributionArtifact.class, name = "PromptContribution"),
    @JsonSubTypes.Type(value = Artifact.ToolCallArtifact.class, name = "ToolCall"),
    @JsonSubTypes.Type(value = Artifact.OutcomeEvidenceArtifact.class, name = "OutcomeEvidence"),
    @JsonSubTypes.Type(value = Artifact.EventArtifact.class, name = "EventArtifact"),
    @JsonSubTypes.Type(value = Artifact.AgentRequestArtifact.class, name = "AgentRequest"),
    @JsonSubTypes.Type(value = Artifact.AgentResultArtifact.class, name = "AgentResult"),
    @JsonSubTypes.Type(value = Artifact.GroupArtifact.class, name = "Group"),
    @JsonSubTypes.Type(value = RefArtifact.class, name = "RefArtifact"),
    @JsonSubTypes.Type(value = MessageStreamArtifact.class, name = "MessageStream")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Artifact permits
        Artifact.ExecutionArtifact,
        Artifact.ExecutionConfigArtifact,
        Artifact.RenderedPromptArtifact,
        Artifact.PromptArgsArtifact,
        Artifact.PromptContributionArtifact,
        Artifact.ToolCallArtifact,
        Artifact.OutcomeEvidenceArtifact,
        Artifact.EventArtifact,
        Artifact.AgentRequestArtifact,
        Artifact.AgentResultArtifact,
        Artifact.GroupArtifact,
        RefArtifact,
        MessageStreamArtifact,
        Templated,
        Artifact.AgentModelArtifact {

    default List<Artifact> collectRecursiveChildren() {
        var l = new ArrayList<>(this.children());
        this.children().stream().flatMap(a -> a.collectRecursiveChildren().stream())
                .forEach(l::add);
        return l;
    }
    
    /**
     * Hierarchical, time-sortable identifier.
     */
    ArtifactKey artifactKey();
    
    /**
     * Type discriminator for serialization.
     */
    String artifactType();
    
    /**
     * SHA-256 hash of content bytes (if applicable).
     */
    Optional<String> contentHash();
    
    /**
     * Optional metadata map.
     */
    Map<String, String> metadata();
    
    /**
     * Child artifacts (tree structure).
     */
    List<Artifact> children();

    record AgentModelArtifact(List<Artifact> children,
                              AgentModel agentModel,
                              Map<String, String> metadata,
                              String hash) implements Artifact {

        @Override
        public ArtifactKey artifactKey() {
            return agentModel.key();
        }

        @Override
        public String artifactType() {
            return agentModel().artifactType();
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    interface HashContext {
        String hash(String in);

        static HashContext defaultHashContext() {
            return in -> {
                try {
                    return SignatureUtil.hashToString(in, SignatureUtil.retrieveDigest());
                } catch (
                        NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    interface AgentModel {

        String computeHash(HashContext hashContext);

        List<AgentModel> children();

        ArtifactKey key();

        default String artifactType() {
            return this.getClass().getSimpleName();
        }

        <T extends AgentModel> T withChildren(List<AgentModel> c);

        default Map<String, String> metadata() {
            return new HashMap<>();
        }

        default Artifact toArtifact(HashContext hashContext) {
            var childArtifacts = children().stream().map(a -> a.toArtifact(hashContext)).toList();
            return new Artifact.AgentModelArtifact(childArtifacts, this, metadata(), computeHash(hashContext));
        }

    }
    
    // ========== Execution Root ==========
    
    /**
     * Root artifact for an execution tree.
     */
    @Builder(toBuilder = true)
    @With
    record ExecutionArtifact(
            ArtifactKey artifactKey,
            String workflowRunId,
            Instant startedAt,
            Instant finishedAt,
            ExecutionStatus status,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "Execution";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.empty();
        }
    }
    
    enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED
    }
    
    // ========== Execution Config ==========
    
    /**
     * Configuration snapshot for reconstructability.
     */
    @Builder(toBuilder = true)
    @With
    record ExecutionConfigArtifact(
            ArtifactKey artifactKey,
            String repositorySnapshotId,
            Map<String, Object> modelRefs,
            Map<String, Object> toolPolicy,
            Map<String, Object> routingPolicy,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "ExecutionConfig";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    // ========== Prompts ==========
    
    /**
     * Fully rendered prompt text with references to template and args.
     */
    @Builder(toBuilder = true)
    @With
    record RenderedPromptArtifact(
            ArtifactKey artifactKey,
            String renderedText,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "RenderedPrompt";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    /**
     * Dynamic inputs bound into a template.
     */
    @Builder(toBuilder = true)
    @With
    record PromptArgsArtifact(
            ArtifactKey artifactKey,
            Map<String, Object> args,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "PromptArgs";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    /**
     * Single prompt contributor output.
     */
    @Builder(toBuilder = true)
    @With
    record PromptContributionArtifact(
            ArtifactKey artifactKey,
            String contributorName,
            int priority,
            List<String> agentTypes,
            String contributedText,
            int orderIndex,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "PromptContribution";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    // ========== Tools ==========
    
    /**
     * Tool invocation with input/output.
     */
    @Builder(toBuilder = true)
    @With
    record ToolCallArtifact(
            ArtifactKey artifactKey,
            String toolCallId,
            String toolName,
            String inputJson,
            String inputHash,
            String outputJson,
            String outputHash,
            String error,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "ToolCall";
        }
        
        @Override
        public Optional<String> contentHash() {
            // Could hash combined input+output
            return Optional.ofNullable(inputHash);
        }
    }
    
    // ========== Outcomes ==========
    
    /**
     * Objective evidence for outcomes.
     */
    @Builder(toBuilder = true)
    @With
    record OutcomeEvidenceArtifact(
            ArtifactKey artifactKey,
            String evidenceType,
            String payload,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "OutcomeEvidence";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    // ========== Events ==========
    
    /**
     * Captured GraphEvent as source artifact.
     */
    @Builder(toBuilder = true)
    @With
    record EventArtifact(
            ArtifactKey artifactKey,
            String eventId,
            String nodeId,
            Instant eventTimestamp,
            String eventType,
            Map<String, Object> payloadJson,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "EventArtifact";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    // ========== Agent Artifacts ==========
    
    /**
     * Serialized agent request.
     */
    @Builder(toBuilder = true)
    @With
    record AgentRequestArtifact(
            ArtifactKey artifactKey,
            String agentType,
            String nodeId,
            String interactionType,
            Map<String, Object> payloadJson,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "AgentRequest";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    /**
     * Serialized agent result.
     */
    @Builder(toBuilder = true)
    @With
    record AgentResultArtifact(
            ArtifactKey artifactKey,
            String agentType,
            String nodeId,
            String interactionType,
            Map<String, Object> payloadJson,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "AgentResult";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }
    
    // ========== Structural ==========
    
    /**
     * Grouping artifact for organizing children (InputArtifacts, AgentExecutionArtifacts, etc.).
     */
    @Builder(toBuilder = true)
    @With
    record GroupArtifact(
            ArtifactKey artifactKey,
            String groupName,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {
        
        @Override
        public String artifactType() {
            return "Group";
        }
        
        @Override
        public Optional<String> contentHash() {
            return Optional.empty();
        }
    }
}
