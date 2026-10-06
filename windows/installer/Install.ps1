param(
    [switch]$Elevated,
    [string]$TargetAppData = [Environment]::GetFolderPath('ApplicationData')
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.IO.Compression.FileSystem

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    $appData = [IO.Path]::GetFullPath($TargetAppData)
    if (-not $appData.EndsWith('\AppData\Roaming', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Não foi possível validar o perfil do usuário que iniciou a instalação.'
    }
    $elevationArgs = @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',('"{0}"' -f $PSCommandPath),'-Elevated','-TargetAppData',('"{0}"' -f $appData))
    try {
        $child = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $elevationArgs -PassThru -Wait
        exit $child.ExitCode
    } catch {
        [System.Windows.Forms.MessageBox]::Show('A instalação precisa de permissão de administrador. Ela foi cancelada ou não foi autorizada.','MarlicoBot PC','OK','Warning') | Out-Null
        exit 1
    }
}

$choice = [System.Windows.Forms.MessageBox]::Show(
    "Instalar o MarlicoBot PC para este computador?`r`n`r`nO instalador solicitará elevação pelo UAC, instalará o programa em Arquivos de Programas e criará atalhos. O agente será iniciado na sessão do usuário, sem privilégios administrativos. A captura de tela continua exigindo confirmação visível no PC.",
    'Instalar MarlicoBot PC', 'YesNo', 'Question')
if ($choice -ne [System.Windows.Forms.DialogResult]::Yes) { exit 0 }

$appData = [IO.Path]::GetFullPath($TargetAppData)
if (-not $appData.EndsWith('\AppData\Roaming', [StringComparison]::OrdinalIgnoreCase)) { throw 'Perfil de instalação inválido.' }
$installRoot = [IO.Path]::GetFullPath((Join-Path $env:ProgramFiles 'MarlicoBotPC'))
$programFilesRoot = [IO.Path]::GetFullPath($env:ProgramFiles) + [IO.Path]::DirectorySeparatorChar
if (-not $installRoot.StartsWith($programFilesRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Destino de instalação inválido.' }
$payload = Join-Path $PSScriptRoot 'MarlicoBotPC-payload.zip'
if (-not (Test-Path -LiteralPath $payload)) { throw 'Os arquivos do programa não foram encontrados no instalador.' }

try { Get-Process -Name MarlicoBotPC -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue } catch {}
try { Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*br.com.marlico.agent.Agent*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } } catch {}
Start-Sleep -Milliseconds 500
if (Test-Path -LiteralPath $installRoot) { Remove-Item -LiteralPath $installRoot -Recurse -Force }
New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
[IO.Compression.ZipFile]::ExtractToDirectory($payload, $installRoot)
$exe = Join-Path $installRoot 'MarlicoBotPC.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw 'O executável não foi extraído corretamente.' }
[IO.File]::WriteAllText((Join-Path $installRoot 'install-user-appdata.txt'), $appData, [Text.Encoding]::UTF8)

$shell = New-Object -ComObject WScript.Shell
$startMenu = Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\MarlicoBot'
$startup = Join-Path $appData 'Microsoft\Windows\Start Menu\Programs\Startup'
$publicDesktop = [Environment]::GetFolderPath('CommonDesktopDirectory')
New-Item -ItemType Directory -Path $startMenu,$startup,$publicDesktop -Force | Out-Null
foreach ($shortcutPath in @((Join-Path $startMenu 'MarlicoBot PC.lnk'),(Join-Path $publicDesktop 'MarlicoBot PC.lnk'),(Join-Path $startup 'MarlicoBot PC.lnk'))) {
    $shortcut = $shell.CreateShortcut($shortcutPath); $shortcut.TargetPath = $exe; $shortcut.WorkingDirectory = $installRoot; $shortcut.Description = 'Painel local do MarlicoBot; captura de tela com confirmação visível'; $shortcut.IconLocation = "$exe,0"; $shortcut.Save()
}
$uninstallKey = 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\MarlicoBotPC'
New-Item -Path $uninstallKey -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name DisplayName -Value 'MarlicoBot PC' -PropertyType String -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name DisplayVersion -Value '1.3.0' -PropertyType String -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name Publisher -Value 'Marlico' -PropertyType String -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name InstallLocation -Value $installRoot -PropertyType String -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name UninstallString -Value ('powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' + (Join-Path $installRoot 'Uninstall.ps1') + '"') -PropertyType String -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name NoModify -Value 1 -PropertyType DWord -Force | Out-Null
New-ItemProperty -Path $uninstallKey -Name NoRepair -Value 1 -PropertyType DWord -Force | Out-Null
[System.Windows.Forms.MessageBox]::Show('MarlicoBot PC foi instalado. Abra-o pelo menu Iniciar. O agente iniciará na sessão deste usuário quando o Windows for iniciado.','Instalação concluída','OK','Information') | Out-Null

