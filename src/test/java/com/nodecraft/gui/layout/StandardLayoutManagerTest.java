package com.nodecraft.gui.layout;

import com.nodecraft.gui.components.EditorComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StandardLayoutManagerTest {

    @Test
    void dividesRegionVerticallyWhenMultipleComponentsShareRegion() {
        StandardLayoutManager manager = new StandardLayoutManager();
        manager.registerComponent(new TestComponent("leftA"),
                new LayoutConstraints(LayoutConstraints.RegionType.NODE_PANEL));
        manager.registerComponent(new TestComponent("leftB"),
                new LayoutConstraints(LayoutConstraints.RegionType.NODE_PANEL));
        manager.registerComponent(new TestComponent("canvas"),
                new LayoutConstraints(LayoutConstraints.RegionType.CANVAS));

        LayoutConfig config = new LayoutConfig(0.25f, 0.75f, 0.0f, 100.0f, 100.0f, 0.0f);
        manager.calculateLayout(800.0f, 600.0f, 10.0f, 20.0f, config, true);

        LayoutDimensions first = manager.getComputedLayout("leftA");
        LayoutDimensions second = manager.getComputedLayout("leftB");

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.x(), second.x(), 0.001f);
        assertEquals(first.width(), second.width(), 0.001f);
        assertEquals(first.y() + first.height(), second.y(), 0.001f);
        assertEquals(300.0f, first.height(), 0.001f);
        assertEquals(300.0f, second.height(), 0.001f);
    }

    private record TestComponent(String id) implements EditorComponent {
        @Override
        public void render(float x, float y, float width, float height, float paddingX, float paddingY) {
        }

        @Override
        public void init() {
        }

        @Override
        public void cleanup() {
        }

        @Override
        public void setVisible(boolean visible) {
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public String getComponentId() {
            return id;
        }

        @Override
        public boolean handleEvent(String eventType, Object data) {
            return false;
        }
    }
}
