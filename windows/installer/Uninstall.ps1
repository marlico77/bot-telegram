$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$installRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$programFilesRoot = [IO.Path]::GetFullPath($env:ProgramFiles) + [IO.Path]::DirectorySeparatorChar
if (-not $installRoot.StartsWith($programFilesRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'A remoção foi cancelada porque o destino não está em Arquivos de Programas.' }
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    $elevationArgs = @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',('"{0}"' -f $PSCommandPath))
    try { $child = Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList $elevationArgs -PassThru -Wait; exit $child.ExitCode }
    catch { [System.Windows.Forms.MessageBox]::Show('A desinstalação precisa de permissão de administrador.','MarlicoBot PC','OK','Warning') | Out-Null; exit 1 }
}
$appData = [IO.File]::ReadAllText((Join-Path $installRoot 'install-user-appdata.txt')).Trim()
if (-not $appData.EndsWith('\AppData\Roaming', [StringComparison]::OrdinalIgnoreCase)) { throw 'O perfil do usuário instalado não pôde ser validado; nenhum arquivo foi removido.' }
try { Get-Process -Name MarlicoBotPC -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue } catch {}
try { Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('java.exe','javaw.exe') -and $_.CommandLine -like '*br.com.marlico.agent.Agent*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } } catch {}
Remove-Item -LiteralPath (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\MarlicoBot') -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path ([Environment]::GetFolderPath('CommonDesktopDirectory')) 'MarlicoBot PC.lnk') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath (Join-Path $appData 'Microsoft\Windows\Start Menu\Programs\Startup\MarlicoBot PC.lnk') -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath 'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\MarlicoBotPC' -Recurse -Force -ErrorAction SilentlyContinue
$cleanup = Join-Path $env:TEMP ('MarlicoBot-remove-' + [guid]::NewGuid().ToString('N') + '.cmd')
@('@echo off','timeout /t 2 /nobreak >nul',('rmdir /s /q "' + $installRoot + '"'),('del /f /q "%~f0"')) | Set-Content -LiteralPath $cleanup -Encoding ASCII
Start-Process -FilePath $env:ComSpec -ArgumentList ('/c "' + $cleanup + '"') -WindowStyle Hidden
[System.Windows.Forms.MessageBox]::Show('MarlicoBot PC foi removido. O pareamento e as configurações em AppData foram preservados.','Desinstalação concluída','OK','Information') | Out-Null
