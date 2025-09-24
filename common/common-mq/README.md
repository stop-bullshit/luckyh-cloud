# Common-MQ 模块使用指南

## 概述

`common-mq` 模块是基于 RabbitMQ 的消息队列增强模块，提供了完整的消息生产者、消费者、以及消息处理的基础设施。

## 模块结构

```
common-mq/
├── src/main/java/com/luckyh/cloud/common/mq/
│   ├── config/
│   │   └── RabbitMQConfig.java          # RabbitMQ配置类
│   ├── message/
│   │   ├── BaseMessage.java             # 消息基类
│   │   ├── UserRegisterMessage.java     # 用户注册消息
│   │   ├── OrderCreateMessage.java      # 订单创建消息
│   │   └── OrderPaymentMessage.java     # 订单支付消息
│   ├── producer/
│   │   └── MessageProducer.java         # 消息生产者工具类
│   └── consumer/
│       ├── BaseMessageConsumer.java     # 消息消费者基类
│       ├── UserMessageConsumer.java     # 用户消息消费者示例
│       └── OrderMessageConsumer.java    # 订单消息消费者示例
```

## 功能特性

### 1. 交换机和队列配置
- **用户主题交换机**: `user.topic.exchange` (Topic 类型)
- **订单直连交换机**: `order.direct.exchange` (Direct 类型)
- **死信交换机**: `dead.letter.exchange` (处理失败消息)

### 2. 消息类型
- **用户注册消息**: 用户注册完成后的异步通知
- **订单创建消息**: 订单创建完成后的异步处理
- **订单支付消息**: 订单支付完成后的异步处理

### 3. 消息可靠性保证
- **消息确认机制**: 手动ACK确保消息处理成功
- **重试机制**: 失败消息自动重试，最大重试3次
- **死信队列**: 超过重试次数的消息进入死信队列
- **消息持久化**: 队列和消息都配置为持久化

### 4. JSON序列化
- 使用 Jackson 进行消息的 JSON 序列化/反序列化
- 统一的消息格式，便于跨服务通信

## 使用方法

### 1. 添加依赖

在需要使用MQ功能的服务中添加依赖：

```xml
<dependency>
    <groupId>com.luckyh.cloud</groupId>
    <artifactId>common-mq</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置 RabbitMQ

在 `application.yml` 中配置 RabbitMQ 连接信息：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual
        retry:
          enabled: true
          max-attempts: 3
```

### 3. 发送消息

```java
@Service
public class UserService {
    
    @Autowired
    private MessageProducer messageProducer;
    
    public void registerUser(User user) {
        // 业务逻辑...
        
        // 发送用户注册消息
        UserRegisterMessage message = new UserRegisterMessage();
        message.setUserId(user.getId());
        message.setUsername(user.getUsername());
        message.setEmail(user.getEmail());
        message.setPhone(user.getPhone());
        message.setRegisterTime(LocalDateTime.now());
        
        messageProducer.sendUserRegisterMessage(message);
    }
}
```

### 4. 消费消息

#### 方式一：继承 BaseMessageConsumer

```java
@Component
public class CustomUserConsumer extends BaseMessageConsumer {
    
    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(name = "custom.user.queue", durable = "true"),
        exchange = @Exchange(name = "user.topic.exchange", type = ExchangeTypes.TOPIC),
        key = "user.register"
    ))
    public void handleMessage(@Payload UserRegisterMessage message,
                            Message rawMessage,
                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                            Channel channel) {
        handleMessage(message, rawMessage, deliveryTag, channel);
    }
    
    @Override
    protected boolean processMessage(BaseMessage message) {
        UserRegisterMessage userMessage = (UserRegisterMessage) message;
        // 实现具体的业务逻辑
        return true; // 返回处理结果
    }
    
    @Override
    protected String getConsumerName() {
        return "CustomUserConsumer";
    }
}
```

#### 方式二：直接使用注解

```java
@Component
public class SimpleConsumer {
    
    @RabbitListener(queues = "user.register.queue")
    public void handleUserRegister(UserRegisterMessage message) {
        // 简单的消息处理逻辑
        log.info("收到用户注册消息: {}", message);
    }
}
```

### 5. 自定义消息类型

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomMessage extends BaseMessage {
    
    private String customField;
    private Integer status;
    // ... 其他字段
}
```

### 6. 延时消息

```java
// 发送延时消息 (需要RabbitMQ延时插件支持)
messageProducer.sendDelayMessage(
    "order.direct.exchange", 
    "order.timeout", 
    message, 
    30000L  // 30秒后执行
);
```

## 监控和管理

### 1. 死信队列监控

所有处理失败的消息都会进入死信队列 `dead.letter.queue`，需要定期监控和处理：

```java
@RabbitListener(queues = "dead.letter.queue")
public void handleDeadLetter(BaseMessage message) {
    // 记录死信消息，发送告警等
    log.error("收到死信消息: {}", message);
}
```

### 2. 消息重试策略

- 默认最大重试次数：3次
- 重试间隔：指数退避算法
- 可通过重写 `getMaxRetries()` 方法自定义重试次数

### 3. RabbitMQ 管理界面

访问 RabbitMQ 管理界面查看队列状态：
- URL: http://localhost:15672
- 默认用户名/密码: guest/guest

## 最佳实践

### 1. 消息设计原则
- 消息体应该包含足够的信息，避免回调查询
- 使用版本号支持消息格式演进
- 避免在消息中传递敏感信息

### 2. 异常处理
- 区分可重试和不可重试的异常
- 对于业务异常，应该直接ACK，避免无意义重试
- 记录详细的错误日志便于排查问题

### 3. 性能优化
- 合理设置预取数量 (prefetch)
- 使用连接池避免频繁创建连接
- 批量处理消息提高吞吐量

### 4. 监控告警
- 监控队列长度，避免消息积压
- 监控死信队列，及时处理异常消息
- 设置消息处理时间告警

## 注意事项

1. **确保幂等性**: 消息可能会重复消费，处理逻辑需要保证幂等性
2. **顺序性**: 如果需要保证消息顺序，使用单个队列和单个消费者
3. **事务性**: 对于需要事务保证的场景，考虑使用本地消息表或Saga模式
4. **容量规划**: 根据业务量合理规划队列容量和消费者数量

## 故障排查

### 常见问题

1. **消息丢失**: 检查消息持久化配置和消费者ACK机制
2. **消息重复**: 检查消费者幂等性实现
3. **消息积压**: 检查消费者处理速度和异常情况
4. **连接异常**: 检查RabbitMQ服务状态和网络连接

### 日志级别配置

```yaml
logging:
  level:
    com.luckyh.cloud.common.mq: DEBUG
    org.springframework.amqp: DEBUG
```