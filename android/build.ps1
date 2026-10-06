param([switch]$SkipChecks)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$root = Split-Path $project -Parent
[xml]$manifest = Get-Content -LiteralPath (Join-Path $project 'AndroidManifest.xml') -Raw
$androidNs = 'http://schemas.android.com/apk/res/android'
$version = $manifest.manifest.GetAttribute('versionName', $androidNs)
$minSdk = $manifest.manifest.'uses-sdk'.GetAttribute('minSdkVersion', $androidNs)
$apkName = "MarlicoBot-$version.apk"
$tools = Join-Path $root '.tools'
$jdk = (Get-ChildItem (Join-Path $tools 'jdk') -Directory | Select-Object -First 1).FullName
$platform = Join-Path $tools 'platform\android-35\android.jar'
$bt = Join-Path $tools 'buildtools\android-15'
if (-not (Test-Path $platform) -or -not (Test-Path "$jdk\bin\javac.exe")) { throw 'Ferramentas ausentes. Consulte android/README.md.' }
$build = Join-Path $project 'build'
$dist = Join-Path $root 'dist'
Set-Location -LiteralPath $root
@('gen','classes','dex','checks') | ForEach-Object { New-Item -ItemType Directory -Path (Join-Path $build $_) -Force | Out-Null }
New-Item -ItemType Directory -Path $dist -Force | Out-Null
function Check-Step([string]$name) { if ($LASTEXITCODE -ne 0) { throw "Falha: $name (código $LASTEXITCODE)" } }
& "$bt\aapt2.exe" compile --dir 'android/res' -o 'android/build/resources.zip'
Check-Step 'compilação dos recursos'
& "$bt\aapt2.exe" link -o 'android/build/unsigned.apk' -I '.tools/platform/android-35/android.jar' --manifest 'android/AndroidManifest.xml' --java 'android/build/gen' -A 'android/assets' --auto-add-overlay 'android/build/resources.zip'
Check-Step 'empacotamento dos recursos'
$sources = @((Get-ChildItem "$project\src","$build\gen" -Filter '*.java' -Recurse).FullName)
& "$jdk\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $platform -d "$build\classes" @sources
Check-Step 'compilação Java'
if (-not $SkipChecks) {
    & "$jdk\bin\javac.exe" -encoding UTF-8 --release 8 -d "$build\checks" "$project\src\br\com\marlico\bot\Core.java" "$project\src\br\com\marlico\bot\Conversation.java" "$project\checks\CoreCheck.java" "$project\checks\ConversationCheck.java"
    Check-Step 'compilação das verificações'
    & "$jdk\bin\java.exe" -cp "$build\checks" CoreCheck
    Check-Step 'verificações de protocolo e validação'
    & "$jdk\bin\java.exe" -cp "$build\checks" ConversationCheck
    Check-Step 'verificações de horários e navegação do Telegram'
}
& "$jdk\bin\jar.exe" cf "$build\classes.jar" -C "$build\classes" .
Check-Step 'arquivo de classes'
& "$jdk\bin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --release --min-api $minSdk --lib $platform --output "$build\dex" "$build\classes.jar"
Check-Step 'conversão para DEX'
& "$jdk\bin\jar.exe" uf "$build\unsigned.apk" -C "$build\dex" classes.dex
Check-Step 'inclusão do DEX'
& "$bt\zipalign.exe" -f -p 4 'android/build/unsigned.apk' 'android/build/aligned.apk'
Check-Step 'alinhamento do APK'
$signing = Join-Path $project 'signing'
New-Item -ItemType Directory -Path $signing -Force | Out-Null
$pass = Join-Path $signing 'password.txt'
$key = Join-Path $signing 'marlicobot.p12'
if (-not (Test-Path $key)) {
    if (-not (Test-Path $pass)) {
        $random = New-Object byte[] 32
        [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($random)
        [IO.File]::WriteAllText($pass,[Convert]::ToBase64String($random),[Text.Encoding]::ASCII)
    }
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $key -storetype PKCS12 -storepass:file $pass -keypass:file $pass -alias marlicobot -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=MarlicoBot, OU=Personal Android App, O=Marlico, C=BR'
    Check-Step 'criação da chave de assinatura'
}
$apk = Join-Path $dist $apkName
& "$jdk\bin\java.exe" -jar "$bt\lib\apksigner.jar" sign --ks $key --ks-key-alias marlicobot --ks-pass "file:$pass" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $apk "$build\aligned.apk"
Check-Step 'assinatura'
& "$jdk\bin\java.exe" -jar "$bt\lib\apksigner.jar" verify --verbose --min-sdk-version $minSdk $apk
Check-Step 'verificação da assinatura'
& "$bt\zipalign.exe" -c -p 4 "dist/$apkName"
Check-Step 'verificação de alinhamento'
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $dist "MarlicoBot-$version.sha256"),"$hash  $apkName`n")
Write-Output "APK gerado: $apk"
Write-Output "SHA256: $hash"
