@echo off
echo 启动Spring Cloud微服务...

echo 正在启动认证服务...
start "Auth Service" cmd /k "cd auth-service && mvn spring-boot:run"

timeout /t 10

echo 正在启动用户服务...
start "User Service" cmd /k "cd user-service && mvn spring-boot:run"

timeout /t 10

echo 正在启动订单服务...
start "Order Service" cmd /k "cd order-service && mvn spring-boot:run"

timeout /t 10

echo 正在启动网关服务...
start "Gateway Service" cmd /k "cd gateway-service && mvn spring-boot:run"

echo 所有服务启动完成！
echo 请访问 http://localhost:8080 进行测试
echo 测试账号：admin/123456 或 user001/123456
pause
