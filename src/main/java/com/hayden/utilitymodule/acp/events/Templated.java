package com.hayden.utilitymodule.acp.events;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contract for versioned templates that can be rendered from dynamic inputs.
 * 
 * All template types implement this interface to provide:
 * - Stable static ID that remains constant across versions
 * - Content hash that changes when template text changes
 * - Artifact key that is stable for a given (staticId, contentHash)
 * - Render operation that produces text from inputs
 */
public non-sealed interface Templated extends Artifact {
    
    /**
     * Hierarchical static ID that identifies the template family.
     * Format: tpl.<category>.<subcategory>...<name>
     * 
     * This ID remains stable across versions - only the content hash changes.
     */
    String templateStaticId();
    
    /**
     * The static template text (no runtime substitutions).
     */
    String templateText();
    
    /**
     * SHA-256 content hash of the template text.
     * Used to identify the specific version within the template family.
     */
    Optional<String> contentHash();
    
    /**
     * Artifact key for this template version.
     * Stable across executions for the same (staticId, contentHash).
     */
    ArtifactKey templateArtifactKey();

    @Override
    List<Artifact> children();

    /**
     * Checks if this template has unresolved placeholders.
     * Templates must be static text - no unresolved variables.
     */
    default boolean hasUnresolvedPlaceholders() {
        String text = templateText();
        if (text == null) {
            return false;
        }
        // Check for common template variable patterns
        return text.contains("{{") && text.contains("}}")
                || text.contains("${") && text.contains("}")
                || text.contains("{%") && text.contains("%}");
    }
    
    /**
     * Validates that this template is properly static.
     * 
     * @throws IllegalStateException if template has unresolved placeholders
     */
    default void validateStatic() {
        if (hasUnresolvedPlaceholders()) {
            throw new IllegalStateException(
                    "Template has unresolved placeholders: " + templateStaticId());
        }
    }
}
