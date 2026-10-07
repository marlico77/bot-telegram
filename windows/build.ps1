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
$setup = Join-Path $root 'dist\MarlicoBotPC-Setup.exe'
$setupStage = Join-Path $build 'setup-stage'
$iexpressStage = Join-Path $env:TEMP 'MarlicoBot-IExpress'
$iexpressSetup = Join-Path $env:TEMP 'MarlicoBotPC-Setup.exe'
$installerScripts = Join-Path $project 'installer'
$ico = Join-Path $build 'marlicobot.ico'
if (-not (Test-Path (Join-Path $jdk 'bin\jpackage.exe'))) { throw 'JDK 17 com jpackage não encontrado em .tools/jdk.' }
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
if (Test-Path $runtime) { Remove-Item -LiteralPath $runtime -Recurse -Force }
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
    $rootResolved = [IO.Path]::GetFullPath($project) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($rootResolved,[StringComparison]::OrdinalIgnoreCase)) { throw 'Destino de build fora da pasta do projeto.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
& (Join-Path $jdk 'bin\jpackage.exe') --type app-image --name MarlicoBotPC --app-version 1.3.3 --description 'Painel local de hardware e software para MarlicoBot' --vendor Marlico --icon $ico --input $inputDir --main-jar MarlicoBotPC.jar --main-class br.com.marlico.agent.Agent --runtime-image $runtime --dest (Split-Path $appImage -Parent)
if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar o aplicativo Windows.' }
Copy-Item -LiteralPath (Join-Path $project 'LEIA-ME.txt') -Destination (Join-Path $appImage 'LEIA-ME.txt') -Force
Copy-Item -LiteralPath (Join-Path $installerScripts 'Uninstall.ps1') -Destination (Join-Path $appImage 'Uninstall.ps1') -Force
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $appImage '*') -DestinationPath $zip -CompressionLevel Optimal
if (Test-Path $setupStage) { $stageFull=[IO.Path]::GetFullPath($setupStage); $buildRoot=[IO.Path]::GetFullPath($build)+[IO.Path]::DirectorySeparatorChar; if(-not $stageFull.StartsWith($buildRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Pasta temporária do instalador fora da área de build.'}; Remove-Item -LiteralPath $stageFull -Recurse -Force }
New-Item -ItemType Directory -Path $setupStage -Force | Out-Null
Copy-Item -LiteralPath $zip -Destination (Join-Path $setupStage 'MarlicoBotPC-payload.zip') -Force
Copy-Item -LiteralPath (Join-Path $installerScripts 'Install.ps1') -Destination (Join-Path $setupStage 'Install.ps1') -Force
if (Test-Path $iexpressStage) { $stageFull=[IO.Path]::GetFullPath($iexpressStage); $tempRoot=[IO.Path]::GetFullPath($env:TEMP)+[IO.Path]::DirectorySeparatorChar; if(-not $stageFull.StartsWith($tempRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Pasta temporária do IExpress fora de TEMP.'}; Remove-Item -LiteralPath $stageFull -Recurse -Force }
New-Item -ItemType Directory -Path $iexpressStage -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $setupStage 'MarlicoBotPC-payload.zip') -Destination (Join-Path $iexpressStage 'MarlicoBotPC-payload.zip') -Force
Copy-Item -LiteralPath (Join-Path $setupStage 'Install.ps1') -Destination (Join-Path $iexpressStage 'Install.ps1') -Force
$sed = Join-Path $iexpressStage 'MarlicoBotPC-Setup.sed'
$sedText = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=1
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=1
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=I
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$iexpressSetup
FriendlyName=Instalador MarlicoBot PC
AppLaunched=powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File Install.ps1
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="Install.ps1"
FILE1="MarlicoBotPC-payload.zip"
[SourceFiles]
SourceFiles0=$iexpressStage\
[SourceFiles0]
%FILE0%=
%FILE1%=
"@
[IO.File]::WriteAllText($sed,$sedText,[Text.Encoding]::Default)
if (Test-Path $iexpressSetup) { $oldSetup=[IO.Path]::GetFullPath($iexpressSetup); $tempRoot=[IO.Path]::GetFullPath($env:TEMP)+[IO.Path]::DirectorySeparatorChar; if(-not $oldSetup.StartsWith($tempRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Saída do IExpress fora de TEMP.'}; Remove-Item -LiteralPath $oldSetup -Force }
& (Join-Path $env:WINDIR 'System32\iexpress.exe') /N $sed
if ($LASTEXITCODE -ne 0) { throw "Falha ao iniciar o IExpress. Código: $LASTEXITCODE" }
$deadline = [DateTime]::UtcNow.AddMinutes(5)
while (-not (Test-Path $iexpressSetup) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Seconds 2 }
if (-not (Test-Path $iexpressSetup) -or (Get-Item -LiteralPath $iexpressSetup).Length -lt 1MB) { throw 'O IExpress não terminou o instalador dentro do tempo esperado.' }
Copy-Item -LiteralPath $iexpressSetup -Destination $setup -Force
Remove-Item -LiteralPath $iexpressSetup -Force
$iconEditor = @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
public static class MarlicoSetupIcon {
    private sealed class ResName { public ushort? Id; public string Name; }
    [UnmanagedFunctionPointer(CallingConvention.Winapi)]
    private delegate bool EnumNamesProc(IntPtr module, IntPtr type, IntPtr name, IntPtr parameter);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern IntPtr LoadLibraryEx(string file, IntPtr fileHandle, uint flags);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern bool FreeLibrary(IntPtr module);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)] private static extern bool EnumResourceNames(IntPtr module, IntPtr type, EnumNamesProc callback, IntPtr parameter);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern IntPtr BeginUpdateResource(string file, bool deleteExisting);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern bool UpdateResource(IntPtr update, IntPtr type, IntPtr name, ushort language, byte[] data, uint size);
    [DllImport("kernel32.dll", SetLastError=true)] private static extern bool EndUpdateResource(IntPtr update, bool discard);
    public static void Apply(string exe, string icoFile) {
        byte[] ico = File.ReadAllBytes(icoFile);
        if (ico.Length < 6 || BitConverter.ToUInt16(ico,0) != 0 || BitConverter.ToUInt16(ico,2) != 1) throw new InvalidDataException("Invalid ICO header.");
        int count = BitConverter.ToUInt16(ico,4);
        if (count < 1 || ico.Length < 6 + count * 16) throw new InvalidDataException("Invalid ICO entries.");
        var groups = new List<ResName>();
        IntPtr module = LoadLibraryEx(exe, IntPtr.Zero, 2);
        if (module == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error(), "Could not load installer resources.");
        EnumNamesProc callback = delegate(IntPtr h, IntPtr t, IntPtr n, IntPtr p) {
            long raw = n.ToInt64();
            if ((raw >> 16) == 0) groups.Add(new ResName { Id = (ushort)(raw & 0xffff) });
            else groups.Add(new ResName { Name = Marshal.PtrToStringUni(n) });
            return true;
        };
        try { if (!EnumResourceNames(module, new IntPtr(14), callback, IntPtr.Zero)) throw new Win32Exception(Marshal.GetLastWin32Error(), "Could not enumerate installer icons."); }
        finally { FreeLibrary(module); GC.KeepAlive(callback); }
        if (groups.Count == 0) throw new InvalidDataException("The installer has no icon resource to replace.");
        byte[] groupData = new byte[6 + count * 14];
        Buffer.BlockCopy(ico,0,groupData,0,6);
        const ushort firstIconId = 1000;
        for (int i=0; i<count; i++) {
            int entry = 6 + i * 16;
            uint bytes = BitConverter.ToUInt32(ico,entry+8), offset = BitConverter.ToUInt32(ico,entry+12);
            if ((ulong)offset + bytes > (ulong)ico.Length || firstIconId + i > ushort.MaxValue) throw new InvalidDataException("Invalid ICO image offset or size.");
            byte[] image = new byte[bytes]; Buffer.BlockCopy(ico,(int)offset,image,0,(int)bytes);
            Buffer.BlockCopy(ico,entry,groupData,6+i*14,12);
            Buffer.BlockCopy(BitConverter.GetBytes((ushort)(firstIconId+i)),0,groupData,6+i*14+12,2);
        }
        IntPtr update = BeginUpdateResource(exe, false);
        if (update == IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error(), "Could not begin installer resource update.");
        bool ended = false;
        try {
            for (int i=0; i<count; i++) {
                int entry = 6 + i * 16; uint bytes = BitConverter.ToUInt32(ico,entry+8), offset = BitConverter.ToUInt32(ico,entry+12);
                byte[] image = new byte[bytes]; Buffer.BlockCopy(ico,(int)offset,image,0,(int)bytes);
                if (!UpdateResource(update,new IntPtr(3),new IntPtr(firstIconId+i),0,image,(uint)image.Length)) throw new Win32Exception(Marshal.GetLastWin32Error(), "Could not write installer icon image.");
            }
            foreach (ResName group in groups) {
                IntPtr name = group.Id.HasValue ? new IntPtr(group.Id.Value) : Marshal.StringToHGlobalUni(group.Name);
                try { if (!UpdateResource(update,new IntPtr(14),name,0,groupData,(uint)groupData.Length)) throw new Win32Exception(Marshal.GetLastWin32Error(), "Could not replace installer icon group."); }
                finally { if (!group.Id.HasValue) Marshal.FreeHGlobal(name); }
            }
            if (!EndUpdateResource(update,false)) throw new Win32Exception(Marshal.GetLastWin32Error(), "Could not save installer icon.");
            ended = true;
        } finally { if (!ended) EndUpdateResource(update,true); }
    }
}
'@
Add-Type -TypeDefinition $iconEditor -Language CSharp
[MarlicoSetupIcon]::Apply($setup,$ico)
Write-Output "Instalador Windows: $setup"
Write-Output 'Logo do MarlicoBot aplicada ao instalador; a assinatura digital é uma etapa separada.'

