[CmdletBinding()]
param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$localDir = Join-Path $repoRoot '.local'
$runDir = Join-Path $localDir 'run'
$logDir = Join-Path $localDir 'logs'
$services = @(
    [pscustomobject]@{ Name = 'luckyh-auth-service'; Port = 8083 }
    [pscustomobject]@{ Name = 'luckyh-user-service'; Port = 8081 }
    [pscustomobject]@{ Name = 'luckyh-inventory-service'; Port = 8084 }
    [pscustomobject]@{ Name = 'luckyh-account-service'; Port = 8085 }
    [pscustomobject]@{ Name = 'luckyh-order-service'; Port = 8082 }
    [pscustomobject]@{ Name = 'luckyh-gateway-service'; Port = 8080 }
)

function Get-ManagedProcess($serviceName, $pidFile) {
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return $null
    }

    try {
        $record = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
        $jarPath = [IO.Path]::GetFullPath([string]$record.JarPath)
        $targetDir = [IO.Path]::GetFullPath((Join-Path (Join-Path $repoRoot $serviceName) 'target'))
        if ($record.Service -ne $serviceName -or
            -not $jarPath.StartsWith($targetDir + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($jarPath) -notmatch ('^' + [regex]::Escape($serviceName) + '-[^\\/]+\.jar$')) {
            return $null
        }

        $process = Get-Process -Id ([int]$record.ProcessId) -ErrorAction Stop
        if ($process.StartTime.ToUniversalTime().Ticks -ne [long]$record.StartTimeUtcTicks) {
            return $null
        }

        $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
        $jarArgument = '(?i)(?:^|\s)-jar\s+"?' + [regex]::Escape($jarPath) + '"?(?:\s|$)'
        if ($details.Name -notin @('java.exe', 'javaw.exe') -or
            -not [regex]::IsMatch([string]$details.CommandLine, $jarArgument)) {
            return $null
        }

        return $process
    } catch {
        return $null
    }
}

function Test-ServiceHealth($port) {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 3
        return $response.status -eq 'UP'
    } catch {
        return $false
    }
}

foreach ($name in @('NACOS_USERNAME', 'NACOS_PASSWORD')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
    }
    if ([string]::IsNullOrWhiteSpace($value)) {
        if ($name -eq 'NACOS_USERNAME') {
            $value = 'nacos'
        } else {
            throw "缺少 $($name)；请在当前进程或 Windows 用户环境变量中设置。"
        }
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

# 逻辑变动: 启动本地服务时从环境变量或集群 Secret 获取 Redis 密码-20261002-1859-01
$redisPassword = [Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'Process')
if ([string]::IsNullOrWhiteSpace($redisPassword)) {
    $redisPassword = [Environment]::GetEnvironmentVariable('REDIS_PASSWORD', 'User')
}
if ([string]::IsNullOrWhiteSpace($redisPassword)) {
    $encodedPassword = & ssh -o BatchMode=yes -o ConnectTimeout=5 k8s-master "kubectl -n redis get secret redis-auth -o jsonpath='{.data.password}'" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($encodedPassword)) {
        throw '无法从 k8s-master 读取 Redis 密码；请检查 SSH 连接，或设置 REDIS_PASSWORD 环境变量。'
    }
    try {
        $redisPassword = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(([string]$encodedPassword).Trim()))
    } catch {
        throw 'Redis Secret 中的密码编码无效。'
    }
}
[Environment]::SetEnvironmentVariable('REDIS_PASSWORD', $redisPassword, 'Process')

# 逻辑变动: 启动本地服务时从环境变量或集群 Secret 获取 RabbitMQ 密码-20261002-2015-01
$rabbitmqPassword = [Environment]::GetEnvironmentVariable('RABBITMQ_PASSWORD', 'Process')
if ([string]::IsNullOrWhiteSpace($rabbitmqPassword)) {
    $rabbitmqPassword = [Environment]::GetEnvironmentVariable('RABBITMQ_PASSWORD', 'User')
}
if ([string]::IsNullOrWhiteSpace($rabbitmqPassword)) {
    $encodedRabbitmqPassword = & ssh -o BatchMode=yes -o ConnectTimeout=5 k8s-master "kubectl -n rabbitmq get secret rabbitmq-auth -o jsonpath='{.data.password}'" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($encodedRabbitmqPassword)) {
        throw '无法从 k8s-master 读取 RabbitMQ 密码；请检查 SSH 连接，或设置 RABBITMQ_PASSWORD 环境变量。'
    }
    try {
        $rabbitmqPassword = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(([string]$encodedRabbitmqPassword).Trim()))
    } catch {
        throw 'RabbitMQ Secret 中的密码编码无效。'
    }
}
[Environment]::SetEnvironmentVariable('RABBITMQ_PASSWORD', $rabbitmqPassword, 'Process')

$discoveryIp = [Environment]::GetEnvironmentVariable('SPRING_CLOUD_NACOS_DISCOVERY_IP', 'Process')
if ([string]::IsNullOrWhiteSpace($discoveryIp)) {
    $discoveryIp = [Environment]::GetEnvironmentVariable('SPRING_CLOUD_NACOS_DISCOVERY_IP', 'User')
}
if ([string]::IsNullOrWhiteSpace($discoveryIp)) {
    $addresses = foreach ($adapter in (Get-NetAdapter -Physical | Where-Object Status -eq 'Up')) {
        $configuration = Get-NetIPConfiguration -InterfaceIndex $adapter.ifIndex
        if (-not $configuration.IPv4DefaultGateway) {
            continue
        }
        $interface = Get-NetIPInterface -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4
        foreach ($address in $configuration.IPv4Address) {
            if ($address.IPAddress -and $address.IPAddress -notmatch '^(169\.254\.|127\.|198\.18\.)') {
                [pscustomobject]@{ Ip = $address.IPAddress; Metric = $interface.InterfaceMetric }
            }
        }
    }
    $discoveryIp = ($addresses | Sort-Object Metric | Select-Object -First 1).Ip
    if ([string]::IsNullOrWhiteSpace($discoveryIp)) {
        throw '未找到有默认网关的已启用物理 IPv4 网卡；可设置 SPRING_CLOUD_NACOS_DISCOVERY_IP。'
    }
}
[Environment]::SetEnvironmentVariable('SPRING_CLOUD_NACOS_DISCOVERY_IP', $discoveryIp, 'Process')
Write-Host "Nacos 服务发现地址：$discoveryIp"

New-Item -ItemType Directory -Path $runDir, $logDir -Force | Out-Null
$running = @{}
foreach ($service in $services) {
    $pidFile = Join-Path $runDir "$($service.Name).json"
    $managed = Get-ManagedProcess $service.Name $pidFile
    if ($managed) {
        $ownListener = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue |
            Where-Object OwningProcess -eq $managed.Id | Select-Object -First 1
        if (-not $ownListener -or -not (Test-ServiceHealth $service.Port)) {
            throw "$($service.Name) 由本脚本启动但健康状态不是 UP；请先检查日志或运行 stop-services.bat。"
        }
        $running[$service.Name] = $true
        Write-Host "$($service.Name) 已运行且健康，跳过。"
        continue
    }
    if (Test-Path -LiteralPath $pidFile) {
        Remove-Item -LiteralPath $pidFile -Force
    }
    $listener = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        throw "端口 $($service.Port) 已被 PID $($listener.OwningProcess) 占用，且不是本脚本管理的 $($service.Name)。"
    }
}

if ($running.Count -eq 0 -and -not $SkipBuild) {
    $maven = (Get-Command mvn -ErrorAction Stop).Source
    Write-Host '正在从根 POM 执行 mvn package...'
    & $maven -f (Join-Path $repoRoot 'pom.xml') package
    if ($LASTEXITCODE -ne 0) {
        throw "mvn package 失败，退出码：$LASTEXITCODE"
    }
} elseif ($running.Count -gt 0 -and -not $SkipBuild) {
    Write-Host '已有服务由本脚本运行，本次跳过重打包；如需更新，请先停止全部服务。'
}

$version = [string]([xml](Get-Content -LiteralPath (Join-Path $repoRoot 'pom.xml') -Raw -Encoding UTF8)).project.version
foreach ($service in $services) {
    if ($running.ContainsKey($service.Name)) {
        continue
    }
    $jarPath = Join-Path (Join-Path $repoRoot $service.Name) "target\$($service.Name)-$version.jar"
    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "缺少 $jarPath；请先执行 mvn package。"
    }
}

$java = (Get-Command java -ErrorAction Stop).Source
$started = @()
try {
    foreach ($service in $services) {
        if ($running.ContainsKey($service.Name)) {
            continue
        }

        $jarPath = [IO.Path]::GetFullPath((Join-Path (Join-Path $repoRoot $service.Name) "target\$($service.Name)-$version.jar"))
        $pidFile = Join-Path $runDir "$($service.Name).json"
        $stdout = Join-Path $logDir "$($service.Name).out.log"
        $stderr = Join-Path $logDir "$($service.Name).err.log"
        $arguments = '-Dfile.encoding=UTF-8 -jar "' + $jarPath + '"'
        Write-Host "启动 $($service.Name)..."
        $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $repoRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        $record = [pscustomobject]@{
            Service = $service.Name
            ProcessId = $process.Id
            StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks
            JarPath = $jarPath
        }
        $started += $record
        $record | ConvertTo-Json -Compress | Set-Content -LiteralPath $pidFile -Encoding utf8

        $deadline = (Get-Date).AddSeconds(120)
        $healthy = $false
        while ((Get-Date) -lt $deadline) {
            $current = Get-Process -Id $process.Id -ErrorAction SilentlyContinue
            if (-not $current -or $current.StartTime.ToUniversalTime().Ticks -ne $record.StartTimeUtcTicks) {
                break
            }
            $ownListener = Get-NetTCPConnection -LocalPort $service.Port -State Listen -ErrorAction SilentlyContinue |
                Where-Object OwningProcess -eq $process.Id | Select-Object -First 1
            if ($ownListener -and (Test-ServiceHealth $service.Port)) {
                $healthy = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $healthy) {
            throw "$($service.Name) 未在 120 秒内达到 UP；日志：$stdout、$stderr"
        }
        Write-Host "$($service.Name) 已就绪：http://127.0.0.1:$($service.Port)/actuator/health"
    }
    Write-Host "六个服务均已就绪。日志与 PID：$localDir"
} catch {
    $failure = $_
    foreach ($record in $started) {
        $managed = Get-ManagedProcess $record.Service (Join-Path $runDir "$($record.Service).json")
        if ($managed -and $managed.Id -eq $record.ProcessId) {
            Stop-Process -Id $managed.Id -Force -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath (Join-Path $runDir "$($record.Service).json") -Force -ErrorAction SilentlyContinue
    }
    throw $failure
}
