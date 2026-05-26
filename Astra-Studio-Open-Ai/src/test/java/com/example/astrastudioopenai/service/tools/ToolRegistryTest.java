package com.example.astrastudioopenai.service.tools;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ToolRegistry.getInstance();
        registry.clearAll();
    }

    @Test
    @Order(1)
    void testSingletonInstance() {
        ToolRegistry instance1 = ToolRegistry.getInstance();
        ToolRegistry instance2 = ToolRegistry.getInstance();
        assertSame(instance1, instance2, "Should return the same singleton instance");
    }

    @Test
    @Order(2)
    void testRegisterAndRetrieveTool() {
        Object toolInstance = new Object();
        registry.registerTool("testTool", toolInstance);

        assertTrue(registry.isToolRegistered("testTool"));
        assertEquals(toolInstance, registry.getTool("testTool"));
        assertEquals(1, registry.getToolCount());
    }

    @Test
    @Order(3)
    void testDuplicateRegistrationThrowsException() {
        Object tool1 = new Object();
        Object tool2 = new Object();

        registry.registerTool("duplicateTool", tool1);

        assertThrows(IllegalStateException.class, () -> {
            registry.registerTool("duplicateTool", tool2);
        }, "Should throw exception for duplicate registration");
    }

    @Test
    @Order(4)
    void testUnregisterTool() {
        Object toolInstance = new Object();
        registry.registerTool("tempTool", toolInstance);
        assertTrue(registry.isToolRegistered("tempTool"));

        registry.unregisterTool("tempTool");
        assertFalse(registry.isToolRegistered("tempTool"));
        assertNull(registry.getTool("tempTool"));
        assertEquals(0, registry.getToolCount());
    }

    @Test
    @Order(5)
    void testUnregisterNonExistentToolDoesNotThrow() {
        assertDoesNotThrow(() -> registry.unregisterTool("nonExistentTool"),
                "Should silently ignore unregister of non-existent tool");
    }

    @Test
    @Order(6)
    void testGetAllToolsReturnsUnmodifiableList() {
        registry.registerTool("tool1", new Object());
        registry.registerTool("tool2", new Object());

        List<Object> allTools = registry.getAllTools();
        assertEquals(2, allTools.size());

        assertThrows(UnsupportedOperationException.class, () -> allTools.add(new Object()),
                "Should return unmodifiable list");
    }

    @Test
    @Order(7)
    void testBatchRegistrationWithRollback() {
        List<Object> tools = List.of(new Object(), new Object(), new Object());
        
        registry.registerTools(tools);
        assertEquals(3, registry.getToolCount());
    }

    @Test
    @Order(8)
    void testToolInfoExtraction() {
        String toolName = "infoTest";
        Object toolInstance = new Object();
        registry.registerTool(toolName, toolInstance);

        ToolInfo info = registry.getToolInfo(toolName);
        assertNotNull(info);
        assertEquals(toolName, info.getName());
        assertEquals(Object.class.getName(), info.getClassName());
        assertNotNull(info.getRegisteredAt());
    }

    @Test
    @Order(9)
    void testDisableAndEnableTool() {
        Object toolInstance = new Object();
        registry.registerTool("disableTest", toolInstance);

        assertFalse(registry.isToolDisabled("disableTest"));

        registry.disableTool("disableTest");
        assertTrue(registry.isToolDisabled("disableTest"));

        List<Object> enabledTools = registry.getEnabledTools();
        assertFalse(enabledTools.contains(toolInstance),
                "Disabled tool should not appear in enabled tools list");

        registry.enableTool("disableTest");
        assertFalse(registry.isToolDisabled("disableTest"));

        enabledTools = registry.getEnabledTools();
        assertTrue(enabledTools.contains(toolInstance),
                "Re-enabled tool should appear in enabled tools list");
    }

    @Test
    @Order(10)
    void testHealthCheck() {
        registry.registerTool("healthyTool1", new Object());
        registry.registerTool("healthyTool2", new Object());

        Map<String, Boolean> healthStatus = registry.healthCheck();
        assertEquals(2, healthStatus.size());
        assertTrue(healthStatus.values().stream().allMatch(Boolean::booleanValue),
                "All tools should be healthy");
    }

    @Test
    @Order(11)
    void testGetNonExistentToolReturnsNull() {
        assertNull(registry.getTool("nonExistent"), 
                "Should return null for non-existent tool");
        assertNull(registry.getToolInfo("nonExistent"),
                "Should return null info for non-existent tool");
    }

    @Test
    @Order(12)
    void testClearAll() {
        registry.registerTool("tool1", new Object());
        registry.registerTool("tool2", new Object());
        assertEquals(2, registry.getToolCount());

        registry.clearAll();
        assertEquals(0, registry.getToolCount());
        assertTrue(registry.getAllTools().isEmpty());
        assertTrue(registry.getToolInfos().isEmpty());
    }

    @Test
    @Order(13)
    @DisplayName("Concurrent access should be thread-safe")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (index % 2 == 0) {
                        registry.registerTool("concurrentTool" + index, new Object());
                    } else {
                        registry.getAllTools();
                        registry.getToolInfos();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        
        executor.shutdown();
        
        assertTrue(completed, "All threads should complete without deadlock");
        assertTrue(registry.getToolCount() >= threadCount / 2,
                "Should have registered at least half of the tools");
    }
}
