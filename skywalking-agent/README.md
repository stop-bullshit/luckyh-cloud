# SkyWalking集成说明

## 1. SkyWalking Agent下载与配置

### 下载SkyWalking Agent
```bash
# 下载SkyWalking 8.16.0
wget https://archive.apache.org/dist/skywalking/8.16.0/apache-skywalking-apm-8.16.0.tar.gz

# 解压
tar -xzf apache-skywalking-apm-8.16.0.tar.gz

# 复制agent到项目目录
cp -r apache-skywalking-apm-bin/agent ./skywalking-agent/
```

### Windows环境下载
1. 访问 https://skywalking.apache.org/downloads/
2. 下载 apache-skywalking-apm-8.16.0.tar.gz
3. 解压后将 `agent` 目录复制到 `luckyh-cloud/skywalking-agent/` 下

## 2. 启动SkyWalking OAP服务器

### 使用Docker启动（推荐）
```bash
# 启动Elasticsearch（SkyWalking存储）
docker run -d --name elasticsearch \
  -p 9200:9200 -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elasticsearch:7.17.9

# 启动SkyWalking OAP
docker run -d --name skywalking-oap \
  --link elasticsearch:elasticsearch \
  -p 11800:11800 -p 12800:12800 \
  -e SW_STORAGE=elasticsearch \
  -e SW_STORAGE_ES_CLUSTER_NODES=elasticsearch:9200 \
  apache/skywalking-oap-server:8.16.0-es7

# 启动SkyWalking UI
docker run -d --name skywalking-ui \
  --link skywalking-oap:skywalking-oap \
  -p 8088:8080 \
  -e SW_OAP_ADDRESS=http://skywalking-oap:12800 \
  apache/skywalking-ui:8.16.0
```

### 使用二进制启动
```bash
# 启动OAP服务器
cd apache-skywalking-apm-bin
./bin/oapService.sh

# 启动UI（新终端）
./bin/webappService.sh
```

## 3. 微服务集成配置

### JVM启动参数配置
每个微服务启动时需要添加以下JVM参数：

```bash
# Gateway Service
java -javaagent:./skywalking-agent/agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=gateway-service \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar gateway-service.jar

# Auth Service  
java -javaagent:./skywalking-agent/agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=auth-service \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar auth-service.jar

# User Service
java -javaagent:./skywalking-agent/agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=user-service \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar user-service.jar

# Order Service
java -javaagent:./skywalking-agent/agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=order-service \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar order-service.jar
```

### IDEA开发环境配置
在IDEA中为每个服务的启动配置添加VM Options：
```
-javaagent:D:\project\demo\luckyh-cloud\skywalking-agent\agent\skywalking-agent.jar
-Dskywalking.agent.service_name=服务名称
-Dskywalking.collector.backend_service=127.0.0.1:11800
```

## 4. 验证SkyWalking集成

### 检查步骤
1. **启动顺序**：
   - Elasticsearch/H2 Database
   - SkyWalking OAP Server  
   - SkyWalking UI
   - 微服务（带Agent）

2. **访问SkyWalking UI**：
   - URL: http://localhost:8088
   - 检查服务是否出现在服务列表中

3. **检查应用日志**：
   ```
   INFO - SkyWalking初始化成功，当前TraceId: xxx
   ```

4. **API测试**：
   - 调用各微服务接口
   - 在SkyWalking UI中查看调用链路

## 5. 常见问题解决

### TraceId为空或异常
- **原因**: Agent未正确加载或OAP服务器未连接
- **解决**: 检查Agent路径、OAP服务器状态、网络连接

### 服务未出现在SkyWalking UI中
- **原因**: Agent配置错误或服务名称冲突
- **解决**: 检查service_name配置，确保唯一性

### Agent启动失败
- **原因**: Java版本不兼容或Agent版本不匹配
- **解决**: 使用Java 8+，确保Agent版本与OAP版本匹配

## 6. 性能调优建议

### 生产环境配置
```properties
# 降低采样率减少性能影响
agent.sample_n_per_3_secs=1

# 限制Span数量
agent.span_limit_per_segment=300

# 关闭调试
agent.is_open_debugging_class=false

# 忽略静态资源
agent.ignore_suffix=.jpg,.jpeg,.js,.css,.png,.bmp,.gif,.ico,.mp3,.mp4,.html,.svg
```

### 监控指标
- **延迟**: 接口响应时间分布
- **吞吐量**: QPS/TPS监控
- **错误率**: 异常和错误统计
- **拓扑**: 服务依赖关系图