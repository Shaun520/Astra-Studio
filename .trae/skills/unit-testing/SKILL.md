---
name: unit-testing
description: Java单元测试编写指南（基于JUnit和Mockito）
version: 1.0.0
category: testing
triggers:
  - unit test
  - 单元测试
  - junit
  - mockito
  - 测试用例
  - test case
---

# 单元测试技能包

编写高质量单元测试的专业指南，基于JUnit 5和Mockito框架。

## 测试原则

1. **测试独立性**：每个测试用例应独立运行，不依赖其他测试的执行顺序或结果
2. **单一职责**：每个测试只验证一个具体的行为或场景
3. **命名清晰**：测试方法名应清楚描述测试场景，推荐格式：`方法名_测试场景_期望结果`
4. **覆盖全面**：包括正常场景、边界条件、异常情况三个维度

## 测试结构（AAA模式）

每个测试用例应遵循Arrange-Act-Assert三段式结构：

```java
@Test
void calculateTotal_withValidItems_returnsCorrectSum() {
    // Arrange - 准备测试数据和依赖
    List<Item> items = Arrays.asList(
        new Item("A", 10.0),
        new Item("B", 20.0)
    );
    Calculator calculator = new Calculator();
    
    // Act - 执行被测试的方法
    double result = calculator.calculateTotal(items);
    
    // Assert - 验证结果是否符合期望
    assertEquals(30.0, result, 0.001);
}
```

## 常用断言

### JUnit 5断言

- `assertEquals(expected, actual)` - 验证相等性
- `assertNotNull(actual)` - 验证非空
- `assertTrue(condition)` / `assertFalse(condition)` - 验证布尔条件
- `assertThrows(Exception.class, () -> {...})` - 验证异常抛出
- `assertAll()` - 批量断言，全部执行
- `assertTimeout(duration, () -> {...})` - 验证执行时间

### AssertJ流式断言（推荐）

```java
assertThat(result)
    .isNotNull()
    .isInstanceOf(User.class)
    .extracting(User::getName)
    .isEqualTo("张三");
```

## Mock使用指南

### 基本Mock

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void getUser_whenExists_returnsUser() {
        // 定义Mock行为
        when(userRepository.findById(1L))
            .thenReturn(Optional.of(new User("张三")));
        
        // 执行测试
        User user = userService.getUser(1L);
        
        // 验证结果
        assertEquals("张三", user.getName());
        
        // 验证方法调用
        verify(userRepository).findById(1L);
    }
}
```

### Mock注意事项

- 使用`@Mock`注解标记依赖
- 使用`@InjectMocks`自动注入Mock对象
- 使用`when().thenReturn()`定义Mock返回值
- 使用`verify()`验证方法是否被调用
- 使用`any()`、`eq()`等匹配器灵活匹配参数

## 边界条件测试清单

必须测试的边界条件：

- **空值（null）**：输入参数为null的情况
- **空集合**：空列表、空Map、空字符串
- **单元素集合**：只有一个元素的集合
- **大量数据**：大数据量场景的性能和正确性
- **边界值**：最大值、最小值、0、-1等
- **特殊字符**：空格、换行符、特殊符号
- **并发场景**：多线程并发访问

## 测试覆盖策略

### 方法级覆盖

每个public方法至少包含以下测试：
- 正常情况测试
- 异常情况测试（参数非法、状态异常等）
- 边界条件测试

### 分支覆盖

- if/else的每个分支都应被测试覆盖
- switch/case的每个case都应测试
- 循环的边界条件（0次、1次、多次）

## 最佳实践

1. **测试先行**：可能的话采用TDD（测试驱动开发）
2. **快速执行**：单元测试应该快速执行，避免依赖外部资源
3. **可重复**：测试结果应该稳定可重复，不依赖执行环境
4. **自动化**：集成到CI/CD流程中自动执行
5. **定期重构**：测试代码也需要重构和维护

## 反模式（避免）

❌ 测试私有方法（应该测试public接口）
❌ 测试第三方库（应该Mock掉）
❌ 测试getter/setter（没有必要）
❌ 过度Mock（导致测试脆弱）
❌ 忽略异常测试
