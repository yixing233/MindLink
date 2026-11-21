# MindLink 构建脚本
#
# 用法（工程根目录）：
#   powershell -ExecutionPolicy Bypass -File scripts/build.ps1
# 产物：build/full-build-<时间与编号>/MindLink-1.0.apk；不覆盖旧候选。
# 可指定已安装 BaselineApk、BaselineSha256 与 ScopedPreparation，仅替换批准的功能类。
#
# 可用环境变量覆盖自动探测：
#   MINDLINK_JDK      JDK 根目录
#   MINDLINK_SDK      Android SDK 根目录
#   MINDLINK_APKTOOL  apktool.jar 路径
#   MINDLINK_PYTHON   python.exe 路径
#   MINDLINK_GRADLE / MINDLINK_SIDEPANEL_GRADLE  两个独立 Gradle 工具链

param(
  [string]$BuildRoot,
  [string]$BaselineApk,
  [string]$BaselineSha256,
  [string]$ScopedPreparation,
  [switch]$CandidateSkipSidePanelLint,
  [switch]$Unsigned,
  [switch]$Online
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ([bool]$BaselineApk -ne [bool]$BaselineSha256) { throw '基线 APK 与 SHA-256 必须同时指定' }
if ($ScopedPreparation -and -not $BaselineApk) { throw '限定范围构建必须指定已安装基线身份' }
if (-not $BuildRoot) { $BuildRoot = "$root\build\full-build-$(Get-Date -Format 'yyyyMMdd-HHmmss')-$([Guid]::NewGuid().ToString('N').Substring(0,8))" }
$BUILD = [IO.Path]::GetFullPath($BuildRoot)
if (Test-Path -LiteralPath $BUILD) { throw '构建目录必须是新目录，不覆盖已有产物' }
$OUT   = "$BUILD\out"
$DEX   = "$BUILD\dex"
$STUB  = "$BUILD\stub"
$KS    = "$root\build\mindlink.jks"
$KSPASS = 'mindlink'
if (-not $Unsigned -and -not (Test-Path $KS)) { throw "原签名密钥缺失，停止构建；可用 -Unsigned 构建未签名包，不会创建替代密钥" }
New-Item -ItemType Directory "$BUILD\logs" -Force | Out-Null

# ============================================================ 工具链探测
function Find-Jdk {
  if ($env:MINDLINK_JDK) { return $env:MINDLINK_JDK }
  foreach ($g in @(
      "C:\Program Files\Microsoft\jdk-*",
      "C:\Program Files\Eclipse Adoptium\jdk-*",
      "C:\Program Files\Java\jdk-*")) {
    $d = Get-ChildItem -Path $g -Directory -ErrorAction SilentlyContinue |
         Where-Object { Test-Path "$($_.FullName)\bin\javac.exe" } |
         Sort-Object Name -Descending | Select-Object -First 1
    if ($d) { return $d.FullName }
  }
  if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { return $env:JAVA_HOME }
  throw "找不到 JDK，请设置 MINDLINK_JDK"
}

function Find-Sdk {
  if ($env:MINDLINK_SDK) { return $env:MINDLINK_SDK }
  $c = @()
  if ($env:LOCALAPPDATA) { $c += "$env:LOCALAPPDATA\Android\Sdk" }
  if ($env:ANDROID_HOME) { $c += $env:ANDROID_HOME }
  if ($env:ANDROID_SDK_ROOT) { $c += $env:ANDROID_SDK_ROOT }
  $c += "C:\Android\Sdk"
  foreach ($d in $c) { if ($d -and (Test-Path "$d\platforms")) { return $d } }
  throw "找不到 Android SDK，请设置 MINDLINK_SDK"
}

function Find-Apktool {
  if ($env:MINDLINK_APKTOOL -and (Test-Path $env:MINDLINK_APKTOOL)) { return $env:MINDLINK_APKTOOL }
  $c = @("$root\tools\apktool.jar")
  if ($env:LOCALAPPDATA) { $c += "$env:LOCALAPPDATA\apktool\apktool.jar" }
  $c += "C:\code\HyperDuo\.tools\bin\apktool.jar"
  foreach ($f in $c) { if ($f -and (Test-Path $f)) { return $f } }
  throw "找不到 apktool.jar，请设置 MINDLINK_APKTOOL"
}

function Find-Python {
  if ($env:MINDLINK_PYTHON -and (Test-Path $env:MINDLINK_PYTHON)) { return $env:MINDLINK_PYTHON }
  $shim = $null
  foreach ($n in @("python", "python3")) {
    $cmd = Get-Command $n -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd -and $cmd.Source) {
      if ($cmd.Source -match "\.exe$") { return $cmd.Source }
      if (-not $shim) { $shim = $cmd.Source }
    }
  }
  foreach ($g in @(
      "$env:USERPROFILE\.pyenv\pyenv-win\versions\*\python.exe",
      "$env:LOCALAPPDATA\Programs\Python\Python3*\python.exe",
      "C:\Python3*\python.exe")) {
    $f = Get-ChildItem -Path $g -ErrorAction SilentlyContinue |
         Sort-Object FullName -Descending | Select-Object -First 1
    if ($f) { return $f.FullName }
  }
  if ($shim) { return $shim }
  throw "找不到 Python 3（合并 dex 需要 zipfile），请设置 MINDLINK_PYTHON"
}

$JDK = Find-Jdk
$SDK = Find-Sdk
$APKTOOL = Find-Apktool
$PYTHON  = Find-Python

$BTDIR = Get-ChildItem "$SDK\build-tools" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path "$($_.FullName)\lib\d8.jar" } |
        Sort-Object Name -Descending | Select-Object -First 1
if (-not $BTDIR) { throw "SDK 里没有可用 build-tools（需 lib/d8.jar）" }
$BT = $BTDIR.FullName

$PLATDIR = Get-ChildItem "$SDK\platforms" -Directory -ErrorAction SilentlyContinue |
           Where-Object { Test-Path "$($_.FullName)\android.jar" } |
           Sort-Object Name -Descending | Select-Object -First 1
if (-not $PLATDIR) { throw "SDK 里没有可用 platform（需 android.jar）" }
$ANDROID_JAR = "$($PLATDIR.FullName)\android.jar"

$JAVA      = "$JDK\bin\java.exe"
$JAVAC     = "$JDK\bin\javac.exe"
$JAR       = "$JDK\bin\jar.exe"
$KEYTOOL   = "$JDK\bin\keytool.exe"
$D8        = "$BT\lib\d8.jar"
$ZIPALIGN  = "$BT\zipalign.exe"
$APKSIGNER = "$BT\lib\apksigner.jar"

Write-Host "JDK    : $JDK"
Write-Host "SDK    : $SDK"
Write-Host "BT     : $BT"
Write-Host "apktool: $APKTOOL"
Write-Host "python : $PYTHON"
Write-Host ""

# ============================================================ 通用调用
# 用 Start-Process 调用外部程序：能拿到真实退出码，也避开 PowerShell 用 &
# 调控制台程序时的各类怪问题。
# Windows 命令行引用：双引号前与参数末尾的反斜杠必须加倍。
function ConvertTo-NativeArgument {
  param([AllowEmptyString()][string]$Value)
  $quoted = [regex]::Replace($Value, '(\\*)"', '$1$1\"')
  $quoted = [regex]::Replace($quoted, '(\\+)$', '$1$1')
  return '"' + $quoted + '"'
}

$script:toolInvocation = 0
function Invoke-Tool {
  param(
    [Parameter(Mandatory)][string]$Exe,
    [Parameter(Mandatory)][string[]]$Arguments,
    [switch]$Quiet
  )
  $script:toolInvocation++
  $outFile = "$BUILD\logs\$($script:toolInvocation)-stdout.txt"
  $errFile = "$BUILD\logs\$($script:toolInvocation)-stderr.txt"
  $commandLine = ($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
  $p = Start-Process -FilePath $Exe -ArgumentList $commandLine -WorkingDirectory $root `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $outFile -RedirectStandardError $errFile
  if (-not $Quiet) {
    if (Test-Path $outFile) { Get-Content $outFile | Where-Object { $_ -ne "" } | ForEach-Object { Write-Host "  $_" } }
    if (Test-Path $errFile) { Get-Content $errFile | Where-Object { $_ -ne "" } | ForEach-Object { Write-Host "  $_" } }
  }
  return $p.ExitCode
}

# 生成 @argfile（绕开命令行长度限制）。
# javac 的 argfile 把反斜杠当转义符，路径统一转正斜杠并引用。
# D8 直接接收类文件 JAR，不依赖其 argfile 的路径解析。
function Write-ArgFile {
  param([string]$Path, [string[]]$Items, [switch]$Quote)
  $out = $Items | ForEach-Object {
    $v = $_.Replace("\", "/")
    if ($Quote) { '"' + $v + '"' } else { $v }
  }
  [IO.File]::WriteAllLines($Path, $out, (New-Object Text.UTF8Encoding $false))
}

# ============================================================ Compose payloads
Write-Host '[UI] 编译共享 Material3 载荷与资源表'
$GRADLE = $env:MINDLINK_GRADLE
if (-not $GRADLE) {
  $GRADLE = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.7.1-bin" -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
$SIDE_GRADLE = $env:MINDLINK_SIDEPANEL_GRADLE
if (-not $SIDE_GRADLE) {
  $SIDE_GRADLE = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.7.1-bin" -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $GRADLE -or -not (Test-Path $GRADLE)) { throw '找不到桌面 Gradle 9.7.1，请设置 MINDLINK_GRADLE' }
if (-not $SIDE_GRADLE -or -not (Test-Path $SIDE_GRADLE)) { throw '找不到侧栏 Gradle 9.7.1，请设置 MINDLINK_SIDEPANEL_GRADLE' }
$env:JAVA_HOME = $JDK
$env:ANDROID_HOME = $SDK
$env:ANDROID_SDK_ROOT = $SDK
$networkArgs = if ($Online) { @() } else { @('--offline') }
$preserveInstalledDesktop = $false
$resourceStage = "$BUILD\resources\res"
$resourceAudit = "$BUILD\resources\staging.json"
$seedIds = "$BUILD\resources\original-stable-ids.txt"
$fullIds = "$BUILD\resources\full-stable-ids.txt"
$desktopInventoryRoot = "$BUILD\desktop-inventory"
$sideInventoryRoot = "$BUILD\sidepanel-inventory"
$desktopInventory = "$desktopInventoryRoot\reports\runtime-artifacts.tsv"
$sideInventory = "$sideInventoryRoot\reports\runtime-artifacts.tsv"
$desktopFinal = "$BUILD\desktop-final"
$sideFinal = "$BUILD\sidepanel-final"
$desktopIds = "$BUILD\resources\desktop-final-ids.txt"
$sideIds = "$BUILD\resources\sidepanel-final-ids.txt"
$code = Invoke-Tool -Exe $GRADLE -Arguments (@('-p', "$root\ui-compose", '--no-daemon', '--console=plain', "-PmdlBuildRoot=$desktopInventoryRoot", ':app:writeRuntimeInventory') + $networkArgs)
if ($code -ne 0) { throw "桌面依赖清单失败（退出码 $code）" }
$code = Invoke-Tool -Exe $SIDE_GRADLE -Arguments (@('-p', "$root\ui-compose\sidepanel", '--no-daemon', '--console=plain', "-PmdlSidePanelBuildRoot=$sideInventoryRoot", 'writeRuntimeInventory') + $networkArgs)
if ($code -ne 0) { throw "侧栏依赖清单失败（退出码 $code）" }
$prepareArgs = @('-I', "$root\scripts\prepare_compose_resources.py", '--source', "$root\res", '--output', $resourceStage,
  '--sidepanel-res', "$root\ui-compose\sidepanel\src\main\res", '--inventory', $desktopInventory,
  '--checksums', "$root\ui-compose\runtime-checksums.json", '--sidepanel-inventory', $sideInventory,
  '--sidepanel-checksums', "$root\ui-compose\sidepanel\runtime-checksums.json", '--public', "$root\res\values\public.xml",
  '--stable-ids-output', $seedIds, '--manifest-output', $resourceAudit)
if (Test-Path "$root\ui-compose\app\src\main\res") { $prepareArgs += @('--desktop-res', "$root\ui-compose\app\src\main\res") }
if ($BaselineApk) { $prepareArgs += @('--baseline-apk', $BaselineApk, '--baseline-sha256', $BaselineSha256) }
$code = Invoke-Tool -Exe $PYTHON -Arguments $prepareArgs
if ($code -ne 0) { throw "共享资源暂存失败（退出码 $code）" }
$bootstrap = "$BUILD\desktop-bootstrap"
$code = Invoke-Tool -Exe $GRADLE -Arguments (@('-p', "$root\ui-compose", '--no-daemon', '--console=plain',
  "-PmdlBuildRoot=$bootstrap", "-PmdlResourceStage=$resourceStage", "-PmdlStableIds=$seedIds", "-PmdlEmitIds=$fullIds", ':app:processReleaseResources') + $networkArgs)
if ($code -ne 0) { throw "共享资源引导链接失败（退出码 $code）" }
$bootstrapApk = "$bootstrap\intermediates\linked_resources_binary_format\release\processReleaseResources\linked-resources-binary-format-release.ap_"
$code = Invoke-Tool -Exe $PYTHON -Arguments @('-I', "$root\scripts\check_compose_resources.py", '--resource-only', '--apk', $bootstrapApk,
  '--public', "$root\res\values\public.xml", '--aapt2', "$BT\aapt2.exe", '--inventory', $desktopInventory,
  '--checksums', "$root\ui-compose\runtime-checksums.json", '--desktop-emitted-ids', $fullIds, '--report', "$BUILD\bootstrap-check.json")
if ($code -ne 0) { throw "引导资源编号检查失败（退出码 $code）" }
# The standalone desktop UI was removed from the product: :app is only linked for the
# shared resource table ($fullIds) and no desktop payload is compiled or shipped.
$sideArgs = @('-p', "$root\ui-compose\sidepanel", '--no-daemon', '--console=plain',
  "-PmdlSidePanelBuildRoot=$sideFinal", "-PmdlSidePanelResourceStage=$resourceStage", "-PmdlSidePanelStableIds=$fullIds", "-PmdlSidePanelEmitIds=$sideIds") + $networkArgs
if ($CandidateSkipSidePanelLint) {
  Write-Warning '仅此候选：侧栏 release lintVital 未执行；编译、AAR 元数据、重复类与资源校验仍必须通过。'
  $sideArgs += '-PmdlSkipPayloadLint=true'
}
$sideArgs += @('assembleRelease', 'writeRuntimeInventory')
$code = Invoke-Tool -Exe $SIDE_GRADLE -Arguments $sideArgs
if ($code -ne 0) { throw "侧栏载荷编译失败（退出码 $code）" }
$SIDE_UI_APK = "$sideFinal\outputs\apk\release\MindLinkSidePanel-release-unsigned.apk"
$dualArgs = @('--sidepanel-ui-apk', $SIDE_UI_APK, '--public', "$root\res\values\public.xml",
  '--stable-ids', $fullIds, '--sidepanel-emitted-ids', $sideIds,
  '--inventory', $desktopInventory, '--checksums', "$root\ui-compose\runtime-checksums.json",
  '--sidepanel-inventory', "$sideFinal\reports\runtime-artifacts.tsv", '--sidepanel-checksums', "$root\ui-compose\sidepanel\runtime-checksums.json",
  '--aapt2', "$BT\aapt2.exe", '--staging-manifest', $resourceAudit)
$checkArgs = @('-I', "$root\scripts\check_compose_resources.py") + $dualArgs + @('--report', "$BUILD\dual-runtime-check.json")
$code = Invoke-Tool -Exe $PYTHON -Arguments $checkArgs
if ($code -ne 0) { throw "侧栏载荷实际资源/DEX/依赖检查失败（退出码 $code）" }

if ($ScopedPreparation) {
  Write-Host '[HOST] 保留已安装基线，仅替换批准的功能类'
  $scoped = "$BUILD\scoped-features"
  $code = Invoke-Tool -Exe $PYTHON -Arguments @('-I', "$root\scripts\build_scoped_features.py", '--baseline', $BaselineApk,
    '--baseline-sha256', $BaselineSha256, '--preparation', $ScopedPreparation, '--output', $scoped,
    '--jdk', $JDK, '--build-tools', $BT, '--android-jar', $ANDROID_JAR)
  if ($code -ne 0) { throw "限定范围功能构建或 ABI 检查失败（退出码 $code）" }
  $DEX = "$scoped\feature"
  $stageApk = "$scoped\stage.apk"
} else {

# ============================================================ 1. stub
Write-Host '[1/7] 编译 Xposed stub'
if (Test-Path $STUB) { Remove-Item $STUB -Recurse -Force }
New-Item -ItemType Directory $STUB | Out-Null
$stubFiles = @(Get-ChildItem "$root\java_stub" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-ArgFile "$BUILD\stub-args.txt" $stubFiles -Quote
$code = Invoke-Tool -Exe $JAVAC -Arguments @(
  "-nowarn", "-source", "11", "-target", "11",
  "-classpath", $ANDROID_JAR, "-encoding", "UTF-8",
  "-d", $STUB, "@$BUILD\stub-args.txt")
if ($code -ne 0) { throw "stub 编译失败（退出码 $code）" }

# ============================================================ 2. java_src
Write-Host '[2/7] 编译 java_src'
if (Test-Path $OUT) { Remove-Item $OUT -Recurse -Force }
New-Item -ItemType Directory $OUT | Out-Null
$srcFiles = @(Get-ChildItem "$root\java_src", "$root\shared-ui\src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-ArgFile "$BUILD\src-args.txt" $srcFiles -Quote
$code = Invoke-Tool -Exe $JAVAC -Arguments @(
  "-nowarn", "-source", "11", "-target", "11",
  "-classpath", "$ANDROID_JAR;$STUB", "-encoding", "UTF-8",
  "-d", $OUT, "@$BUILD\src-args.txt")
if ($code -ne 0) { throw "java_src 编译失败（退出码 $code）" }

# ============================================================ 3. d8
Write-Host '[3/7] d8 生成 classes2.dex'
if (Test-Path $DEX) { Remove-Item $DEX -Recurse -Force }
New-Item -ItemType Directory $DEX | Out-Null
$classesJar = "$BUILD\classes.jar"
if (Test-Path $classesJar) { Remove-Item $classesJar -Force }
$code = Invoke-Tool -Exe $JAR -Arguments @("--create", "--file", $classesJar, "-C", $OUT, ".")
if ($code -ne 0) { throw "类文件打包失败（退出码 $code）" }
$code = Invoke-Tool -Exe $JAVA -Arguments @(
  "-cp", $D8, "com.android.tools.r8.D8",
  "--lib", $ANDROID_JAR, "--min-api", "33", "--output", $DEX, $classesJar)
if ($code -ne 0) { throw "d8 失败（退出码 $code）" }

# ============================================================ 4. apktool
Write-Host '[4/7] apktool 回编'
# 全源码路线显式使用独立输出；安装基线候选使用上面的 scoped 路线。
$env:PATH = "$BT;$env:PATH"
$stageApk = "$BUILD\stage.apk"
$code = Invoke-Tool -Exe $JAVA -Arguments @('-jar', $APKTOOL, 'b', '.', '-o', $stageApk)
if ($code -ne 0) { throw "apktool 回编失败（退出码 $code）" }
}

# ============================================================ 5. merge
Write-Host '[5/7] 合并功能 DEX 与共享 Material3 载荷'
$mergeArgs = @('-I', "$root\scripts\merge.py", $stageApk, $DEX, "$BUILD\merged.apk") + $dualArgs + @('--report', "$BUILD\merge-check.json")
$code = Invoke-Tool -Exe $PYTHON -Arguments $mergeArgs
if ($code -ne 0) { throw "合并失败（退出码 $code）" }

# ============================================================ 6. align
Write-Host '[6/7] zipalign'
if (Test-Path "$BUILD\aligned.apk") { Remove-Item "$BUILD\aligned.apk" -Force }
$code = Invoke-Tool -Exe $ZIPALIGN -Arguments @("-p", "-f", "4", "$BUILD\merged.apk", "$BUILD\aligned.apk")
if ($code -ne 0) { throw "zipalign 失败（退出码 $code）" }

# ============================================================ 7. sign
Write-Host '[7/7] 签名'
if ($Unsigned) {
  $final = "$BUILD\MindLink-1.0-unsigned.apk"
  Copy-Item -LiteralPath "$BUILD\aligned.apk" -Destination $final
  Write-Host "未签名构建完成（不能直接安装）：$final"
  Write-Host ("大小：{0:N2} MiB" -f ((Get-Item -LiteralPath $final).Length / 1MB))
  return
}
if (-not (Test-Path $KS)) { throw "原签名密钥缺失，停止签名" }

$final = "$BUILD\MindLink-1.0.apk"
if (Test-Path $final) { Remove-Item $final -Force }
$code = Invoke-Tool -Exe $JAVA -Arguments @(
  "-jar", $APKSIGNER, "sign",
  "--ks", $KS, "--ks-pass", "pass:$KSPASS", "--key-pass", "pass:$KSPASS",
  "--ks-key-alias", "mindlink", "--out", $final, "$BUILD\aligned.apk")
if ($code -ne 0) { throw "签名失败（退出码 $code）" }

$code = Invoke-Tool -Exe $JAVA -Arguments @('-jar', $APKSIGNER, 'verify', '--verbose', '--print-certs', $final)
if ($code -ne 0) { throw "签名校验失败（退出码 $code）" }
$code = Invoke-Tool -Exe $ZIPALIGN -Arguments @('-c', '-p', '4', $final)
if ($code -ne 0) { throw "最终签名包对齐检查失败（退出码 $code）" }
if ($BaselineApk) {
  $code = Invoke-Tool -Exe $PYTHON -Arguments @('-I', "$root\scripts\tests\apk_checks.py", '--apk', $final, '--baseline', $BaselineApk,
    '--jdk', $JDK, '--build-tools', $BT, '--output', "$BUILD\apk-verification")
  if ($code -ne 0) { throw "最终包身份、资源、载荷或原签名检查失败（退出码 $code）" }
}
$summary = [ordered]@{
  apk = $final
  sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $final).Hash.ToLowerInvariant()
  bytes = (Get-Item -LiteralPath $final).Length
  baseline = $BaselineApk
  baselineSha256 = $BaselineSha256
  desktopPayload = 'shared-sidepanel-runtime'
  sidePanelLintVital = $(if ($CandidateSkipSidePanelLint) { 'not-executed-explicit-offline-candidate' } else { 'executed' })
  deviceInstalled = $false
}
[IO.File]::WriteAllText("$BUILD\build-result.json", ($summary | ConvertTo-Json -Depth 8) + "`n", (New-Object Text.UTF8Encoding $false))

Write-Host ""
Write-Host "完成：$final"
Write-Host ("大小：{0:N2} MB" -f ((Get-Item $final).Length / 1MB))
