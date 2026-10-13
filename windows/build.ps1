param([switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$root = Split-Path $project -Parent
$jdk = (Get-ChildItem (Join-Path $root '.tools\jdk') -Directory | Select-Object -First 1).FullName
$build = Join-Path $project 'build'
$classes = Join-Path $build 'classes'
$inputDir = Join-Path $build 'package-input'
$runtime = Join-Path $build 'runtime'
$appImage = Join-Path $project 'dist\MarlicoBotPC'
$zip = Join-Path $root 'dist\MarlicoBotPC-Windows-x64.zip'
$msi = Join-Path $root 'dist\MarlicoBotPC-Setup.msi'
$msiStage = Join-Path $build 'msi'
$ico = Join-Path $build 'marlicobot.ico'
$version = '1.3.4'
# Keep this UUID stable. Windows Installer uses it to replace later MSI releases.
$upgradeUuid = '2799c98c-b64c-4b12-b44c-02ad5aec1941'
if (-not (Test-Path (Join-Path $jdk 'bin\jpackage.exe'))) { throw 'JDK 17 com jpackage não encontrado em .tools/jdk.' }

# JDK 17 jpackage needs WiX 3 (candle.exe and light.exe) to create a real MSI.
$wixCandidates = @()
if ($env:WIX_HOME) { $wixCandidates += $env:WIX_HOME }
$wixCandidates += (Join-Path $root '.tools\wix')
$wixCandidates += 'C:\Program Files (x86)\WiX Toolset v3.11\bin'
$wixCandidates += 'C:\Program Files (x86)\WiX Toolset v3.14\bin'
$wixBin = $null
foreach ($candidate in $wixCandidates) {
    if ((Test-Path (Join-Path $candidate 'candle.exe')) -and (Test-Path (Join-Path $candidate 'light.exe'))) {
        $wixBin = (Resolve-Path -LiteralPath $candidate).Path
        break
    }
}
if ($wixBin) {
    $env:PATH = "$wixBin;$env:PATH"
} elseif (-not ((Get-Command candle.exe -ErrorAction SilentlyContinue) -and (Get-Command light.exe -ErrorAction SilentlyContinue))) {
    throw 'WiX Toolset 3 não encontrado. Instale WiX 3.11/3.14 ou coloque candle.exe e light.exe em .tools/wix (ou defina WIX_HOME).'
}

New-Item -ItemType Directory -Force $classes,$inputDir,(Split-Path $zip -Parent) | Out-Null
$sources = @((Get-ChildItem "$project\src","$root\shared\src" -Filter '*.java' -Recurse).FullName)
& (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 --release 17 --add-modules jdk.httpserver -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o agente Windows.' }
if (-not $SkipChecks) {
    & (Join-Path $jdk 'bin\java.exe') --add-modules jdk.httpserver -cp $classes br.com.marlico.agent.Agent --inventory-check
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao consultar hardware e programas instalados neste Windows.' }
}
Copy-Item -LiteralPath (Join-Path $project 'assets\marlicobot-logo.png') -Destination (Join-Path $classes 'br\com\marlico\agent\marlicobot-logo.png') -Force
& (Join-Path $jdk 'bin\jar.exe') --create --file (Join-Path $inputDir 'MarlicoBotPC.jar') --main-class br.com.marlico.agent.Agent -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Falha ao montar o aplicativo Java.' }
if (-not $SkipChecks) {
    & (Join-Path $jdk 'bin\java.exe') --add-modules jdk.httpserver -cp (Join-Path $inputDir 'MarlicoBotPC.jar') br.com.marlico.agent.Agent --self-test
    if ($LASTEXITCODE -ne 0) { throw 'Falha nas verificações locais da API do agente Windows.' }
}

$runtimeFull = [IO.Path]::GetFullPath($runtime)
$buildRoot = [IO.Path]::GetFullPath($build) + [IO.Path]::DirectorySeparatorChar
if (-not $runtimeFull.StartsWith($buildRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Runtime fora da área de build.' }
if (Test-Path $runtimeFull) { Remove-Item -LiteralPath $runtimeFull -Recurse -Force }
& (Join-Path $jdk 'bin\jlink.exe') --add-modules java.desktop,java.management,jdk.management,jdk.httpserver,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o runtime do Windows.' }

Add-Type -AssemblyName System.Drawing
$logo = [Drawing.Image]::FromFile((Join-Path $project 'assets\marlicobot-logo.png'))
try {
    $payloads = @(); $sizes = @(16,32,48,256)
    foreach ($size in $sizes) { $bitmap = New-Object Drawing.Bitmap($size,$size); $g = [Drawing.Graphics]::FromImage($bitmap); try { $g.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic; $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality; $g.DrawImage($logo,0,0,$size,$size); $mem = New-Object IO.MemoryStream; try { $bitmap.Save($mem,[Drawing.Imaging.ImageFormat]::Png); $payloads += ,$mem.ToArray() } finally { $mem.Dispose() } } finally { $g.Dispose(); $bitmap.Dispose() } }
    $stream = New-Object IO.MemoryStream; $writer = New-Object IO.BinaryWriter($stream)
    try { $writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]$sizes.Count); $offset = 6 + 16 * $sizes.Count; for ($i=0; $i -lt $sizes.Count; $i++) { $size=$sizes[$i]; $png=$payloads[$i]; $writer.Write([byte]$(if($size -eq 256){0}else{$size})); $writer.Write([byte]$(if($size -eq 256){0}else{$size})); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([UInt16]1); $writer.Write([UInt16]32); $writer.Write([UInt32]$png.Length); $writer.Write([UInt32]$offset); $offset += $png.Length }; foreach($png in $payloads){$writer.Write($png)}; $writer.Flush(); [IO.File]::WriteAllBytes($ico,$stream.ToArray()) } finally { $writer.Dispose(); $stream.Dispose() }
} finally { $logo.Dispose() }

if (Test-Path $appImage) {
    $resolved = [IO.Path]::GetFullPath($appImage)
    $projectRoot = [IO.Path]::GetFullPath($project) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($projectRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Destino de build fora da pasta do projeto.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
& (Join-Path $jdk 'bin\jpackage.exe') --type app-image --name MarlicoBotPC --app-version $version --description 'Painel local de hardware e software para MarlicoBot' --vendor Marlico --icon $ico --input $inputDir --main-jar MarlicoBotPC.jar --main-class br.com.marlico.agent.Agent --runtime-image $runtime --dest (Split-Path $appImage -Parent)
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar o aplicativo Windows.' }
Copy-Item -LiteralPath (Join-Path $project 'LEIA-ME.txt') -Destination (Join-Path $appImage 'LEIA-ME.txt') -Force
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $appImage '*') -DestinationPath $zip -CompressionLevel Optimal

$msiStageFull = [IO.Path]::GetFullPath($msiStage)
if (-not $msiStageFull.StartsWith($buildRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Pasta do MSI fora da área de build.' }
if (Test-Path $msiStageFull) { Remove-Item -LiteralPath $msiStageFull -Recurse -Force }
New-Item -ItemType Directory -Path $msiStageFull | Out-Null
& (Join-Path $jdk 'bin\jpackage.exe') --type msi --name MarlicoBotPC --app-version $version --vendor Marlico --description 'Agente de monitoramento MarlicoBot' --app-image $appImage --icon $ico --win-per-user-install --win-menu --win-menu-group MarlicoBot --win-shortcut --win-upgrade-uuid $upgradeUuid --dest $msiStage
if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar MSI. Confira a instalação do WiX Toolset 3.' }
$generated = @(Get-ChildItem -LiteralPath $msiStage -Filter '*.msi' -File)
if ($generated.Count -ne 1) { throw 'O jpackage não produziu exatamente um MSI.' }
Copy-Item -LiteralPath $generated[0].FullName -Destination $msi -Force
Write-Host "Instalador Windows MSI: $msi"
Write-Host "Pacote portátil: $zip"
