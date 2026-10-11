package com.nodecraft.gui.components.property.core;

import com.nodecraft.nodesystem.core.BaseNode;
import com.nodecraft.nodesystem.execution.ExecutionContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyEditSessionTest {

    @Test
    void tempValuesAreOwnedAndReusable() {
        PropertyEditSession session = new PropertyEditSession();
        AtomicInteger creates = new AtomicInteger();
        String key = "n_prop";

        float[] first = session.getOrCreateTempValue(key, () -> {
            creates.incrementAndGet();
            return new float[]{1.0f};
        });
        float[] second = session.getOrCreateTempValue(key, () -> {
            creates.incrementAndGet();
            return new float[]{2.0f};
        });

        assertSame(first, second);
        assertEquals(1, creates.get());
    }

    @Test
    void getOrReplaceTempValueReplacesWrongType() {
        PropertyEditSession session = new PropertyEditSession();
        session.putTempValue("k", Integer.valueOf(1));
        String replaced = session.getOrReplaceTempValue("k", String.class, () -> "ok");
        assertEquals("ok", replaced);
        assertEquals("ok", session.getTempValue("k"));
    }

    @Test
    void errorCountsDisableAfterThreshold() {
        PropertyEditSession session = new PropertyEditSession();
        StubNode node = new StubNode();
        assertEquals(1, session.recordPropertyError(node, "alpha"));
        assertEquals(2, session.recordPropertyError(node, "alpha"));
        assertFalse(session.isPropertyDisabled(node, "alpha", 3));
        assertEquals(3, session.recordPropertyError(node, "alpha"));
        assertTrue(session.isPropertyDisabled(node, "alpha", 3));
        session.clearPropertyError(node, "alpha");
        assertFalse(session.isPropertyDisabled(node, "alpha", 3));
    }

    @Test
    void errorCountsAreScopedPerNode() {
        PropertyEditSession session = new PropertyEditSession();
        StubNode nodeA = new StubNode();
        StubNode nodeB = new StubNode();

        session.recordPropertyError(nodeA, "size");
        session.recordPropertyError(nodeA, "size");
        session.recordPropertyError(nodeA, "size");

        assertTrue(session.isPropertyDisabled(nodeA, "size", 3));
        assertFalse(session.isPropertyDisabled(nodeB, "size", 3));
    }

    @Test
    void clearForNodeDropsTempAndLocksForThatNodeOnly() {
        PropertyEditSession session = new PropertyEditSession();
        StubNode keep = new StubNode();
        StubNode drop = new StubNode();

        session.getOrCreateTempValue(session.getTempValueKey(keep, "a"), () -> "keep");
        session.getOrCreateTempValue(session.getTempValueKey(drop, "a"), () -> "drop");
        session.markPropertyBeingEdited(drop, "a");
        session.recordPropertyError(drop, "a");
        session.recordPropertyError(keep, "a");

        session.clearForNode(drop);

        assertEquals("keep", session.getTempValue(session.getTempValueKey(keep, "a")));
        assertEquals(null, session.getTempValue(session.getTempValueKey(drop, "a")));
        assertFalse(session.isPropertyBeingEdited(drop, "a"));
        assertEquals(1, session.getPropertyErrorCount(keep, "a"));
        assertEquals(0, session.getPropertyErrorCount(drop, "a"));
    }

    @Test
    void refreshKeepsEditLockAlivePastTimeout() throws InterruptedException {
        PropertyEditSession session = new PropertyEditSession();
        StubNode node = new StubNode();

        session.markPropertyBeingEdited(node, "value");
        Thread.sleep(2_100L);
        session.refreshPropertyBeingEdited(node, "value");

        assertTrue(session.isPropertyBeingEdited(node, "value"));
    }

    @Test
    void orphanEditLockExpiresWithoutRefresh() throws InterruptedException {
        PropertyEditSession session = new PropertyEditSession();
        StubNode node = new StubNode();

        session.markPropertyBeingEdited(node, "value");
        Thread.sleep(2_100L);
        session.checkAndCleanExpiredEditLocks();

        assertFalse(session.isPropertyBeingEdited(node, "value"));
    }

    private static final class StubNode extends BaseNode {
        StubNode() {
            super(UUID.randomUUID(), "test.stub.property_edit_session");
        }

        @Override
        public String getDisplayName() {
            return "Stub";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public void processNode(@Nullable ExecutionContext context) {
        }
    }
}
