package com.nodecraft.nodesystem.world;

import org.jetbrains.annotations.Nullable;

/**
 * Internal scan status for world.read region/column scanners. Not a graph datatype.
 */
public final class WorldScanStatus {

    private boolean hitLimit;
    private boolean sawUnloaded;
    private boolean budgetStop;
    private String limitReason = "max_blocks";
    private String error = "";

    public void markHitLimit(String reason) {
        hitLimit = true;
        if (reason != null && !reason.isBlank()) {
            limitReason = reason;
        }
    }

    public void markUnloaded() {
        sawUnloaded = true;
    }

    public void markBudgetStop() {
        budgetStop = true;
    }

    public void fail(@Nullable String message) {
        error = message == null ? "" : message;
    }

    public boolean hitLimit() {
        return hitLimit;
    }

    public boolean sawUnloaded() {
        return sawUnloaded;
    }

    public boolean budgetStop() {
        return budgetStop;
    }

    public boolean valid() {
        return error.isEmpty();
    }

    public String error() {
        return error;
    }

    public boolean accept(WorldQueryAccess.Status status) {
        if (status == WorldQueryAccess.Status.UNLOADED) {
            markUnloaded();
            return false;
        }
        if (status == WorldQueryAccess.Status.BUDGET) {
            markBudgetStop();
            return false;
        }
        return true;
    }

    public boolean complete() {
        return valid() && !hitLimit && !sawUnloaded && !budgetStop;
    }

    public String stoppedReason() {
        if (!valid()) {
            return "invalid";
        }
        if (budgetStop) {
            return "work_budget";
        }
        if (hitLimit) {
            return limitReason;
        }
        if (sawUnloaded) {
            return "unloaded_chunk";
        }
        return "completed";
    }
}
