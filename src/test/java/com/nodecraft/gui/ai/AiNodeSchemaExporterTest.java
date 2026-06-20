package com.nodecraft.gui.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiNodeSchemaExporterTest {

    @Test
    void exportsRuntimeSchemaAsJsonAndMarkdown() {
        List<AiNodeSchemaCatalog.NodeSchema> schemas = List.of(sampleSchema("geometry.primitives.box"));

        AiNodeSchemaExporter.ExportSnapshot snapshot = AiNodeSchemaExporter.snapshot(schemas);

        assertEquals(1, snapshot.nodeCount());
        assertTrue(snapshot.json().contains("\"schemaExportVersion\""));
        assertTrue(snapshot.json().contains("\"typeId\": \"geometry.primitives.box\""));
        assertTrue(snapshot.markdown().contains("# NodeCraft AI Node Schema"));
        assertTrue(snapshot.markdown().contains("`geometry.primitives.box`"));
        assertTrue(snapshot.markdown().contains(snapshot.revision()));
    }

    @Test
    void revisionChangesWhenSchemaChanges() {
        String first = AiNodeSchemaExporter.computeRevision(List.of(sampleSchema("geometry.primitives.box")));
        String second = AiNodeSchemaExporter.computeRevision(List.of(sampleSchema("geometry.primitives.sphere")));

        assertNotEquals(first, second);
        assertEquals(first, AiNodeSchemaExporter.computeRevision(List.of(sampleSchema("geometry.primitives.box"))));
    }

    @Test
    void systemPromptIncludesRuntimeSchemaRevision() {
        List<AiNodeSchemaCatalog.NodeSchema> schemas = List.of(sampleSchema("output.preview.geometry_viewer"));

        String systemPrompt = AiPromptBuilder.buildSystemPrompt(schemas);

        assertTrue(systemPrompt.contains("Runtime schema revision:"));
        assertTrue(systemPrompt.contains("output.preview.geometry_viewer"));
    }

    private static AiNodeSchemaCatalog.NodeSchema sampleSchema(String typeId) {
        return new AiNodeSchemaCatalog.NodeSchema(
            typeId,
            "Sample",
            "Sample node",
            "geometry.primitives",
            List.of(new AiNodeSchemaCatalog.PortSchema("input_radius", null, "float", false, null)),
            List.of(new AiNodeSchemaCatalog.PortSchema("output_geometry", null, "geometry", false, null)),
            List.of(new AiNodeSchemaCatalog.ParamSchema("radius", "number"))
        );
    }
}
