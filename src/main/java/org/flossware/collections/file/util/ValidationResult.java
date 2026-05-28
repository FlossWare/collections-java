package org.flossware.collections.file.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of validating a file-backed collection file.
 */
public class ValidationResult {
    private final boolean valid;
    private final List<String> errors;
    private final List<String> warnings;
    private final long totalEntries;
    private final long validEntries;
    private final long corruptedEntries;

    private ValidationResult(Builder builder) {
        this.valid = builder.errors.isEmpty();
        this.errors = Collections.unmodifiableList(new ArrayList<>(builder.errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
        this.totalEntries = builder.totalEntries;
        this.validEntries = builder.validEntries;
        this.corruptedEntries = builder.corruptedEntries;
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public long getTotalEntries() {
        return totalEntries;
    }

    public long getValidEntries() {
        return validEntries;
    }

    public long getCorruptedEntries() {
        return corruptedEntries;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{\n");
        sb.append("  valid=").append(valid).append("\n");
        sb.append("  totalEntries=").append(totalEntries).append("\n");
        sb.append("  validEntries=").append(validEntries).append("\n");
        sb.append("  corruptedEntries=").append(corruptedEntries).append("\n");
        if (!errors.isEmpty()) {
            sb.append("  errors=[\n");
            for (String error : errors) {
                sb.append("    - ").append(error).append("\n");
            }
            sb.append("  ]\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("  warnings=[\n");
            for (String warning : warnings) {
                sb.append("    - ").append(warning).append("\n");
            }
            sb.append("  ]\n");
        }
        sb.append("}");
        return sb.toString();
    }

    public static class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private long totalEntries = 0;
        private long validEntries = 0;
        private long corruptedEntries = 0;

        public Builder addError(String error) {
            errors.add(error);
            return this;
        }

        public Builder addWarning(String warning) {
            warnings.add(warning);
            return this;
        }

        public Builder totalEntries(long total) {
            this.totalEntries = total;
            return this;
        }

        public Builder validEntries(long valid) {
            this.validEntries = valid;
            return this;
        }

        public Builder corruptedEntries(long corrupted) {
            this.corruptedEntries = corrupted;
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(this);
        }
    }
}
