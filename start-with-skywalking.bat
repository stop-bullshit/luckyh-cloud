@echo off
echo Starting LuckyH Cloud Services with SkyWalking Agent...

REM 检查SkyWalking Agent是否存在
if not exist "skywalking-agent\agent\skywalking-agent.jar" (
    echo Error: SkyWalking Agent not found!
    echo Please download SkyWalking 8.16.0 and copy agent directory to skywalking-agent/
    pause
    exit /b 1
)

REM 设置公共的JVM参数
set SKYWALKING_OPTS=-javaagent:skywalking-agent\agent\skywalking-agent.jar -Dskywalking.collector.backend_service=127.0.0.1:11800

echo.
echo Starting Gateway Service...
start "Gateway Service" java %SKYWALKING_OPTS% -Dskywalking.agent.service_name=gateway-service -jar gateway-service\target\gateway-service-1.0.0.jar

timeout /t 10

echo Starting Auth Service...
start "Auth Service" java %SKYWALKING_OPTS% -Dskywalking.agent.service_name=auth-service -jar auth-service\target\auth-service-1.0.0.jar

timeout /t 10

echo Starting User Service...
start "User Service" java %SKYWALKING_OPTS% -Dskywalking.agent.service_name=user-service -jar user-service\target\user-service-1.0.0.jar

timeout /t 10

echo Starting Order Service...
start "Order Service" java %SKYWALKING_OPTS% -Dskywalking.agent.service_name=order-service -jar order-service\target\order-service-1.0.0.jar

echo.
echo All services started with SkyWalking Agent!
echo SkyWalking UI: http://localhost:8088
echo Gateway: http://localhost:8080
echo.
pause