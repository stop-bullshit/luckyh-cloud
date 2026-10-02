$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$runDir = Join-Path (Join-Path $repoRoot '.local') 'run'
$services = @('luckyh-gateway-service', 'luckyh-order-service', 'luckyh-account-service', 'luckyh-inventory-service', 'luckyh-user-service', 'luckyh-auth-service')

function Get-ManagedProcess($serviceName, $pidFile) {
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

$failed = $false
foreach ($serviceName in $services) {
    $pidFile = Join-Path $runDir "$serviceName.json"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        Write-Host "$serviceName 没有本脚本的 PID 记录，跳过。"
        continue
    }

    $process = Get-ManagedProcess $serviceName $pidFile
    if (-not $process) {
        Write-Warning "$serviceName 的 PID、启动时间或 JAR 命令行不匹配，未停止任何进程。"
        Remove-Item -LiteralPath $pidFile -Force
        continue
    }

    try {
        Stop-Process -Id $process.Id -Force
        if (-not $process.WaitForExit(10000)) {
            throw "PID $($process.Id) 仍在运行。"
        }
        Remove-Item -LiteralPath $pidFile -Force
        Write-Host "$serviceName 已停止。"
    } catch {
        Write-Warning "$serviceName 停止失败：$($_.Exception.Message)"
        $failed = $true
    }
}

if ($failed) {
    exit 1
}
