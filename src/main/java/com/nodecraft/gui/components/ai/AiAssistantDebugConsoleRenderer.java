package com.nodecraft.gui.components.ai;

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
                if (ImGui.beginChild("aiDebugRawBody", 0.0f, 280.0f, true)) {
                    if (state.rawResponse() == null || state.rawResponse().isBlank()) {
                        ImGui.textDisabled("No raw response available.");
                    } else {
                        ImGui.textWrapped(state.rawResponse());
                    }
                }
                ImGui.endChild();
                if (ImGui.button("Copy Raw Response")) {
                    actions.copyRawResponse();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Model Text")) {
                if (ImGui.beginChild("aiDebugModelTextBody", 0.0f, 280.0f, true)) {
                    if (state.modelText() == null || state.modelText().isBlank()) {
                        ImGui.textDisabled("No extracted model text available.");
                    } else {
                        ImGui.textWrapped(state.modelText());
                    }
                }
                ImGui.endChild();
                if (ImGui.button("Copy Model Text")) {
                    actions.copyModelText();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Request Snapshot")) {
                ImGui.textDisabled("API key is masked for safety.");
                if (ImGui.beginChild("aiDebugRequestSnapshotBody", 0.0f, 260.0f, true)) {
                    if (state.requestSnapshot() == null || state.requestSnapshot().isBlank()) {
                        ImGui.textDisabled("No request snapshot available.");
                    } else {
                        ImGui.textWrapped(state.requestSnapshot());
                    }
                }
                ImGui.endChild();
                if (ImGui.button("Copy Request Snapshot")) {
                    actions.copyRequestSnapshot();
                }
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Export")) {
                if (ImGui.beginChild("aiDebugExportBody", 0.0f, 260.0f, true)) {
                    ImGui.textDisabled("Preview (compact):");
                    ImGui.textWrapped(state.compactDiagnostics() == null ? "" : state.compactDiagnostics());
                }
                ImGui.endChild();
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
