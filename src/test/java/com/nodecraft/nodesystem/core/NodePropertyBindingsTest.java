package com.nodecraft.nodesystem.core;

import com.nodecraft.gui.components.property.core.PropertyDescriptor;
import com.nodecraft.gui.components.property.core.PropertyInspector;
import com.nodecraft.nodesystem.nodes.geometry.boolops.SdfBoxNode;
import com.nodecraft.nodesystem.nodes.world.write.CloneRegionNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodePropertyBindingsTest {

    @Test
    void sdfBoxNodePropertiesSurviveStateRoundTrip() {
        SdfBoxNode original = new SdfBoxNode();
        NodePropertyBindings.deserialize(original, Map.of(
                "halfX", 11.0,
                "halfY", 22.0,
                "halfZ", 33.0
        ));

        Object savedState = original.getNodeState();
        assertInstanceOf(Map.class, savedState);
        @SuppressWarnings("unchecked")
        Map<String, Object> savedMap = (Map<String, Object>) savedState;
        assertEquals(11.0, savedMap.get("halfX"));
        assertEquals(22.0, savedMap.get("halfY"));
        assertEquals(33.0, savedMap.get("halfZ"));

        SdfBoxNode loaded = new SdfBoxNode();
        loaded.setNodeState(savedState);

        Object reloadedState = loaded.getNodeState();
        assertNotNull(reloadedState);
        @SuppressWarnings("unchecked")
        Map<String, Object> reloadedMap = (Map<String, Object>) reloadedState;
        assertEquals(11.0, reloadedMap.get("halfX"));
        assertEquals(22.0, reloadedMap.get("halfY"));
        assertEquals(33.0, reloadedMap.get("halfZ"));
    }

    @Test
    void cloneRegionNodeBeanPropertiesSurviveStateRoundTrip() {
        CloneRegionNode original = new CloneRegionNode();
        original.setMaxBlocks(4096);
        original.setCloneMode(CloneRegionNode.CloneMode.MASKED);
        original.setIncludeEntities(true);
        original.setRecordUndo(false);

        Object savedState = original.getNodeState();
        assertInstanceOf(Map.class, savedState);
        @SuppressWarnings("unchecked")
        Map<String, Object> savedMap = (Map<String, Object>) savedState;
        assertEquals(4096, savedMap.get("maxBlocks"));
        assertEquals(CloneRegionNode.CloneMode.MASKED, savedMap.get("cloneMode"));
        assertEquals(true, savedMap.get("includeEntities"));
        assertEquals(false, savedMap.get("recordUndo"));

        CloneRegionNode loaded = new CloneRegionNode();
        loaded.setNodeState(savedState);

        assertEquals(4096, loaded.getMaxBlocks());
        assertEquals(CloneRegionNode.CloneMode.MASKED, loaded.getCloneMode());
        assertTrue(loaded.isIncludeEntities());
        assertFalse(loaded.isRecordUndo());
    }

    @Test
    void propertyInspectorDiscoversSameBindingsAsSerializer() {
        PropertyInspector inspector = new PropertyInspector();
        List<PropertyDescriptor> descriptors = inspector.getPropertiesForNode(SdfBoxNode.class);
        List<NodePropertyBindings.NodePropertyBinding> bindings = NodePropertyBindings.bindingsFor(SdfBoxNode.class);

        assertEquals(bindings.size(), descriptors.size());
        for (int i = 0; i < bindings.size(); i++) {
            NodePropertyBindings.NodePropertyBinding binding = bindings.get(i);
            PropertyDescriptor descriptor = descriptors.stream()
                    .filter(candidate -> candidate.name.equals(binding.name()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(binding.displayName(), descriptor.displayName);
            assertEquals(binding.type(), descriptor.type);
            assertEquals(binding.writable(), descriptor.setter != null);
            assertEquals(binding.category(), descriptor.category);
            assertEquals(binding.order(), descriptor.order);
        }
    }

    @Test
    void enumPropertiesCoerceFromStringAfterJsonStyleRoundTrip() {
        CloneRegionNode original = new CloneRegionNode();
        original.setCloneMode(CloneRegionNode.CloneMode.MOVE);

        NodePropertyBindings.deserialize(original, Map.of("cloneMode", "MOVE"));
        assertEquals(CloneRegionNode.CloneMode.MOVE, original.getCloneMode());
    }
}
