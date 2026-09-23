package com.nodecraft.gui.components.ai;

import com.nodecraft.gui.layout.ImGuiChildScope;
import imgui.ImGui;

final class AiAssistantDebugConsoleRenderer {

    private AiAssistantDebugConsoleRenderer() {
    }

    record State(
            String errorCategory,
            int attempts,
            String rawResponse,
            String modelText,
            String requestSnapshot,
            String compactDiagnostics,
            String fullDiagnostics
    ) {
    }

    interface Actions {
        void renderFailureSummarySection();

        void copyRawResponse();

        void copyModelText();

        void copyRequestSnapshot();

        void copyCompactExport();

        void copyFullExport();
    }

    static void renderDebugConsolePopup(State state, Actions actions) {
        int flags = 0;
        if (!ImGui.beginPopupModal("AI Debug Console", flags)) {
            return;
        }

        actions.renderFailureSummarySection();
        ImGui.spacing();

        String categoryText = state.errorCategory() == null || state.errorCategory().isBlank()
                ? "none"
                : state.errorCategory();
        ImGui.textDisabled("Category: " + categoryText + " | Attempts: " + state.attempts());
        ImGui.separator();

        if (ImGui.beginTabBar("aiDebugConsoleTabs")) {
            if (ImGui.beginTabItem("Raw Response")) {
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugRawBody", 0.0f, 280.0f, true, 0)) {
                    if (scope.isOpen()) {
                        if (state.rawResponse() == null || state.rawResponse().isBlank()) {
                            ImGui.textDisabled("No raw response available.");
                        } else {
                            ImGui.textWrapped(state.rawResponse());
                        }
                    }
                }
                if (ImGui.button("Copy Raw Response")) {
                    actions.copyRawResponse();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Model Text")) {
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugModelTextBody", 0.0f, 280.0f, true, 0)) {
                    if (scope.isOpen()) {
                        if (state.modelText() == null || state.modelText().isBlank()) {
                            ImGui.textDisabled("No extracted model text available.");
                        } else {
                            ImGui.textWrapped(state.modelText());
                        }
                    }
                }
                if (ImGui.button("Copy Model Text")) {
                    actions.copyModelText();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Request Snapshot")) {
                ImGui.textDisabled("API key is masked for safety.");
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugRequestSnapshotBody", 0.0f, 260.0f, true, 0)) {
                    if (scope.isOpen()) {
                        if (state.requestSnapshot() == null || state.requestSnapshot().isBlank()) {
                            ImGui.textDisabled("No request snapshot available.");
                        } else {
                            ImGui.textWrapped(state.requestSnapshot());
                        }
                    }
                }
                if (ImGui.button("Copy Request Snapshot")) {
                    actions.copyRequestSnapshot();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Export")) {
                try (ImGuiChildScope scope = new ImGuiChildScope("aiDebugExportBody", 0.0f, 260.0f, true, 0)) {
                    if (scope.isOpen()) {
                        ImGui.textDisabled("Preview (compact):");
                        ImGui.textWrapped(state.compactDiagnostics() == null ? "" : state.compactDiagnostics());
                    }
                }
                if (ImGui.button("Copy Compact Export")) {
                    actions.copyCompactExport();
                }
                ImGui.sameLine();
                if (ImGui.button("Copy Full Export")) {
                    actions.copyFullExport();
                }
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }

        if (ImGui.button("Close")) {
            ImGui.closeCurrentPopup();
        }

        ImGui.endPopup();
    }
}
