#!/bin/bash

echo "Starting LuckyH Cloud Services with SkyWalking Agent..."

# 检查SkyWalking Agent是否存在
if [ ! -f "skywalking-agent/agent/skywalking-agent.jar" ]; then
    echo "Error: SkyWalking Agent not found!"
    echo "Please download SkyWalking 8.16.0 and copy agent directory to skywalking-agent/"
    exit 1
fi

# 设置公共的JVM参数
SKYWALKING_OPTS="-javaagent:skywalking-agent/agent/skywalking-agent.jar -Dskywalking.collector.backend_service=127.0.0.1:11800"

echo
echo "Starting Gateway Service..."
nohup java $SKYWALKING_OPTS -Dskywalking.agent.service_name=gateway-service -jar gateway-service/target/gateway-service-1.0.0.jar > logs/gateway.log 2>&1 &

sleep 10

echo "Starting Auth Service..."
nohup java $SKYWALKING_OPTS -Dskywalking.agent.service_name=auth-service -jar auth-service/target/auth-service-1.0.0.jar > logs/auth.log 2>&1 &

sleep 10

echo "Starting User Service..."
nohup java $SKYWALKING_OPTS -Dskywalking.agent.service_name=user-service -jar user-service/target/user-service-1.0.0.jar > logs/user.log 2>&1 &

sleep 10

echo "Starting Order Service..."
nohup java $SKYWALKING_OPTS -Dskywalking.agent.service_name=order-service -jar order-service/target/order-service-1.0.0.jar > logs/order.log 2>&1 &

echo
echo "All services started with SkyWalking Agent!"
echo "SkyWalking UI: http://localhost:8088"
echo "Gateway: http://localhost:8080"
echo
echo "Check logs in logs/ directory"