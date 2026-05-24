package org.flossware.jcollections.file.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of repairing a corrupted file-backed collection file.
 */
public class RepairResult {
    private final boolean success;
    private final List<String> messages;
    private final long entriesRecovered;
    private final long entriesLost;
    private final long originalFileSize;
    private final long repairedFileSize;

    private RepairResult(Builder builder) {
        this.success = builder.success;
        this.messages = Collections.unmodifiableList(new ArrayList<>(builder.messages));
        this.entriesRecovered = builder.entriesRecovered;
        this.entriesLost = builder.entriesLost;
        this.originalFileSize = builder.originalFileSize;
        this.repairedFileSize = builder.repairedFileSize;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<String> getMessages() {
        return messages;
    }

    public long getEntriesRecovered() {
        return entriesRecovered;
    }

    public long getEntriesLost() {
        return entriesLost;
    }

    public long getOriginalFileSize() {
        return originalFileSize;
    }

    public long getRepairedFileSize() {
        return repairedFileSize;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RepairResult{\n");
        sb.append("  success=").append(success).append("\n");
        sb.append("  entriesRecovered=").append(entriesRecovered).append("\n");
        sb.append("  entriesLost=").append(entriesLost).append("\n");
        sb.append("  originalFileSize=").append(originalFileSize).append(" bytes\n");
        sb.append("  repairedFileSize=").append(repairedFileSize).append(" bytes\n");
        if (!messages.isEmpty()) {
            sb.append("  messages=[\n");
            for (String message : messages) {
                sb.append("    - ").append(message).append("\n");
            }
            sb.append("  ]\n");
        }
        sb.append("}");
        return sb.toString();
    }

    public static class Builder {
        private boolean success = false;
        private final List<String> messages = new ArrayList<>();
        private long entriesRecovered = 0;
        private long entriesLost = 0;
        private long originalFileSize = 0;
        private long repairedFileSize = 0;

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder addMessage(String message) {
            messages.add(message);
            return this;
        }

        public Builder entriesRecovered(long recovered) {
            this.entriesRecovered = recovered;
            return this;
        }

        public Builder entriesLost(long lost) {
            this.entriesLost = lost;
            return this;
        }

        public Builder originalFileSize(long size) {
            this.originalFileSize = size;
            return this;
        }

        public Builder repairedFileSize(long size) {
            this.repairedFileSize = size;
            return this;
        }

        public RepairResult build() {
            return new RepairResult(this);
        }
    }
}
