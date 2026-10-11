package com.nodecraft.gui.components.property.core;

import com.nodecraft.gui.components.property.support.PropertyConstraintResolver;
import com.nodecraft.nodesystem.api.NodeProperty;
import com.nodecraft.nodesystem.nodes.output.preview.GeometryViewerNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PropertyConstraintsTest {

    @Test
    void fromAnnotationParsesBounds() throws Exception {
        Field field = GeometryViewerNode.class.getDeclaredField("transparency");
        NodeProperty annotation = field.getAnnotation(NodeProperty.class);

        PropertyConstraints constraints = PropertyConstraints.fromAnnotation(annotation);
        assertEquals(0.0d, constraints.min());
        assertEquals(1.0d, constraints.max());
        assertFalse(constraints.hasStep());
    }

    @Test
    void mergeKeepsTighterBounds() {
        PropertyConstraints annotationBounds = new PropertyConstraints(0.0d, 10.0d, null);
        PropertyConstraints semanticBounds = new PropertyConstraints(0.0d, 1.0d, null);

        PropertyConstraints merged = annotationBounds.merge(semanticBounds);
        assertEquals(0.0d, merged.min());
        assertEquals(1.0d, merged.max());
    }

    @Test
    void clampDoubleRespectsBounds() {
        PropertyConstraints constraints = new PropertyConstraints(0.001d, 20000.0d, null);
        assertEquals(0.001d, constraints.clampDouble(0.0d));
        assertEquals(20000.0d, constraints.clampDouble(99999.0d));
        assertEquals(42.0d, constraints.clampDouble(42.0d));
    }

    @Test
    void geometryViewerTransparencyResolvesToPercentRange() {
        GeometryViewerNode node = new GeometryViewerNode();
        PropertyInspector inspector = new PropertyInspector();
        PropertyDescriptor transparency = inspector.getPropertiesForNode(GeometryViewerNode.class).stream()
                .filter(prop -> "transparency".equals(prop.name))
                .findFirst()
                .orElseThrow();

        PropertyConstraints resolved = PropertyConstraintResolver.resolve(node, transparency);
        assertEquals(0.0d, resolved.min());
        assertEquals(1.0d, resolved.max());
    }

    @Test
    void unboundedAnnotationReturnsSharedInstance() {
        assertSame(PropertyConstraints.UNBOUNDED, PropertyConstraints.fromAnnotation(null));
    }

    @Test
    void contradictoryMergeThrows() {
        PropertyConstraints maxOne = new PropertyConstraints(null, 1.0d, null);
        PropertyConstraints minFive = new PropertyConstraints(5.0d, null, null);
        assertThrows(IllegalArgumentException.class, () -> maxOne.merge(minFive));
    }

    @Test
    void nonFiniteBoundsAreIgnored() {
        PropertyConstraints constraints = new PropertyConstraints(Double.NaN, Double.POSITIVE_INFINITY, null);
        assertFalse(constraints.hasMin());
        assertFalse(constraints.hasMax());
    }
}
