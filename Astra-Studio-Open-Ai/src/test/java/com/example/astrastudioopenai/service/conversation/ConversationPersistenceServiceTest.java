package com.example.astrastudioopenai.service.conversation;

import com.example.astrastudioopenai.entity.ConversationEntity;
import com.example.astrastudioopenai.entity.MessageEntity;
import com.example.astrastudioopenai.repository.ConversationRepository;
import com.example.astrastudioopenai.repository.MessageRepository;
import com.example.astrastudioopenai.repository.SnapshotRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationPersistenceServiceTest {

    @Mock
    private KryoSerializer kryoSerializer;

    @Mock
    private ConversationCacheService cacheService;

    @Mock
    private ConversationRepository conversationRepo;

    @Mock
    private MessageRepository messageRepo;

    @Mock
    private SnapshotRepository snapshotRepo;

    @Mock
    private ConversationQueryService conversationQueryService;

    private ConversationPersistenceService persistenceService;

    @BeforeEach
    void setUp() throws Exception {
        persistenceService = new ConversationPersistenceService(
                kryoSerializer,
                cacheService,
                conversationRepo,
                messageRepo,
                snapshotRepo,
                conversationQueryService);

        setPrivateField(persistenceService, "enabled", true);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("应该正确保存用户消息")
    void shouldSaveUserMessageCorrectly() {
        String memoryId = "test_conv_001";
        String userMessage = "Hello, this is a test message";

        ConversationEntity mockConv = createMockConversation(memoryId, 0);
        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.of(mockConv));
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId)).thenReturn(java.util.Optional.empty());
        when(messageRepo.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.saveUserMessage(memoryId, userMessage);

        verify(messageRepo, times(1)).save(argThat(msg -> msg.getRole().equals("user") &&
                msg.getContent().equals(userMessage) &&
                msg.getSequenceNum() == 0));
        verify(conversationRepo, times(1)).save(argThat(conv -> conv.getMessageCount() == 1));
    }

    @Test
    @DisplayName("应该正确保存助手消息")
    void shouldSaveAssistantMessageCorrectly() {
        String memoryId = "test_conv_002";
        String assistantContent = "Hi! How can I help you?";
        String thinkingContent = "Let me think...";

        ConversationEntity mockConv = createMockConversation(memoryId, 1);
        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.of(mockConv));
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId)).thenReturn(java.util.Optional.of(0));
        when(messageRepo.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.saveAssistantMessage(memoryId, assistantContent, thinkingContent);

        verify(messageRepo, times(1)).save(argThat(msg -> msg.getRole().equals("assistant") &&
                msg.getContent().equals(assistantContent) &&
                msg.getThinkingContent().equals(thinkingContent) &&
                msg.getSequenceNum() == 1));
        verify(conversationRepo, times(1)).save(argThat(conv -> conv.getMessageCount() == 2));
    }

    @Test
    @DisplayName("应该为不存在的会话创建新会话")
    void shouldCreateNewConversationIfNotExists() {
        String memoryId = "new_conv_001";
        String userMessage = "First message";

        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.empty());
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId)).thenReturn(java.util.Optional.empty());
        when(messageRepo.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepo.save(any(ConversationEntity.class)))
                .thenAnswer(invocation -> {
                    ConversationEntity conv = invocation.getArgument(0);
                    if (conv.getId() == null) {
                        conv.setId(1L);
                    }
                    return conv;
                });

        persistenceService.saveUserMessage(memoryId, userMessage);

        verify(conversationRepo, atLeast(1)).save(argThat(conv -> conv.getMemoryId().equals(memoryId)));
    }

    @Test
    @DisplayName("并发保存消息时应该生成唯一的序列号")
    void shouldGenerateUniqueSequenceNumbersUnderConcurrency() throws Exception {
        String memoryId = "concurrent_conv_001";
        int threadCount = 10;
        int messagesPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        List<Integer> generatedSequenceNumbers = new CopyOnWriteArrayList<>();

        ConversationEntity mockConv = createMockConversation(memoryId, 0);
        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.of(mockConv));
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId)).thenReturn(java.util.Optional.empty());
        when(messageRepo.save(any(MessageEntity.class)))
                .thenAnswer(invocation -> {
                    MessageEntity msg = invocation.getArgument(0);
                    generatedSequenceNumbers.add(msg.getSequenceNum());
                    return msg;
                });
        when(conversationRepo.save(any(ConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    for (int j = 0; j < messagesPerThread; j++) {
                        if (threadIndex % 2 == 0) {
                            persistenceService.saveUserMessage(memoryId, "User message " + threadIndex + "-" + j);
                        } else {
                            persistenceService.saveAssistantMessage(memoryId,
                                    "Assistant reply " + threadIndex + "-" + j, null);
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Concurrent test failed: " + e.getMessage());
                }
                return null;
            }));
        }

        latch.countDown();

        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();

        int expectedTotalMessages = threadCount * messagesPerThread;
        assertEquals(expectedTotalMessages, successCount.get(), "All messages should be saved successfully");
        assertEquals(expectedTotalMessages, generatedSequenceNumbers.size(),
                "All sequence numbers should be collected");

        long uniqueSequenceNumbers = generatedSequenceNumbers.stream()
                .distinct()
                .count();

        assertEquals(expectedTotalMessages, uniqueSequenceNumbers,
                "All sequence numbers should be unique under concurrency");

        System.out.println(
                "✅ Concurrency test passed: " + expectedTotalMessages + " messages with unique sequence numbers");
    }

    @Test
    @DisplayName("序列号应该按顺序递增")
    void sequenceNumbersShouldIncrementSequentially() {
        String memoryId = "sequential_conv_001";

        ConversationEntity mockConv = createMockConversation(memoryId, 0);
        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.of(mockConv));
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId)).thenReturn(java.util.Optional.empty());
        when(messageRepo.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepo.save(any(ConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                persistenceService.saveUserMessage(memoryId, "User message " + i);
            } else {
                persistenceService.saveAssistantMessage(memoryId, "Assistant message " + i, null);
            }
        }

        verify(messageRepo, times(10)).save(argThat(msg -> {
            assertTrue(msg.getSequenceNum() >= 0 && msg.getSequenceNum() < 10,
                    "Sequence number should be in range [0, 9], got: " + msg.getSequenceNum());
            return true;
        }));
    }

    @Test
    @DisplayName("当messageCount接近Integer.MAX_VALUE时应发出警告")
    void shouldWarnWhenApproachingIntegerLimit() throws Exception {
        String memoryId = "limit_test_conv";

        ConversationEntity mockConv = createMockConversation(memoryId, Integer.MAX_VALUE - 1);
        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.of(mockConv));
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId))
                .thenReturn(java.util.Optional.of(Integer.MAX_VALUE - 2));
        when(messageRepo.save(any(MessageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepo.save(any(ConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        persistenceService.saveUserMessage(memoryId, "Test message near limit");

        verify(conversationRepo, times(1)).save(argThat(conv -> conv.getMessageCount() == Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("禁用持久化时不应该保存消息")
    void shouldNotSaveWhenDisabled() throws Exception {
        setPrivateField(persistenceService, "enabled", false);

        String memoryId = "disabled_conv";
        persistenceService.saveUserMessage(memoryId, "This should not be saved");

        verifyNoInteractions(messageRepo);
        verifyNoInteractions(conversationRepo);
    }

    @Test
    @DisplayName("保存失败时不应该影响计数器状态")
    void shouldHandleSaveFailureGracefully() {
        String memoryId = "failure_test_conv";

        ConversationEntity mockConv = createMockConversation(memoryId, 0);
        when(conversationRepo.findByMemoryId(memoryId)).thenReturn(java.util.Optional.of(mockConv));
        when(messageRepo.findMaxSequenceNumByMemoryId(memoryId)).thenReturn(java.util.Optional.empty());
        when(messageRepo.save(any(MessageEntity.class))).thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () -> {
            persistenceService.saveUserMessage(memoryId, "This will fail");
        });

        verify(messageRepo, times(1)).save(any(MessageEntity.class));
    }

    private ConversationEntity createMockConversation(String memoryId, int messageCount) {
        ConversationEntity conv = new ConversationEntity();
        conv.setId(1L);
        conv.setMemoryId(memoryId);
        conv.setTitle("Test Conversation");
        conv.setModelName("deepseek-v4-flash");
        conv.setMessageCount(messageCount);
        conv.setStatus((short) 1);
        return conv;
    }
}
