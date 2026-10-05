param([switch]$Verify)
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
$env:GRADLE_USER_HOME='C:\Chacademi\tools\gradle-cache'
Set-Location -LiteralPath 'C:\Chacademi\build-workspace'
$tasks=if($Verify){@('verifyServer','assembleServer')}else{@('assembleServer')}
& '.\gradlew.bat' --no-daemon --console=plain @tasks
if($LASTEXITCODE -ne 0){throw 'Remote server plugin build failed'}
$dest='C:\Chacademi\artifacts\server'
New-Item -ItemType Directory -Path $dest -Force|Out-Null
foreach($project in @('magic-codex-paper','magic-discovery-paper','creature-spawns-paper','tornado-event-paper')){
    Get-ChildItem -LiteralPath "$project\build\libs" -File -Filter '*.jar'|Where-Object {$_.Name -notmatch '-sources|-javadoc'}|Copy-Item -Destination $dest -Force
}
Get-ChildItem -LiteralPath $dest -File -Filter '*.jar'|ForEach-Object {[pscustomobject]@{Name=$_.Name;Bytes=$_.Length;SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}}|ConvertTo-Json
