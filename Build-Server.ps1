param([switch]$Verify)
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
$env:Path=$env:JAVA_HOME+'\bin;'+$env:Path
$env:GRADLE_USER_HOME='C:\Chacademi\tools\gradle-cache'
Set-Location -LiteralPath $PSScriptRoot
$tasks=if($Verify){@('verifyServer','assembleServer')}else{@('assembleServer')}
& '.\gradlew.bat' --no-daemon --console=plain @tasks
if($LASTEXITCODE -ne 0){throw 'Remote server plugin build failed'}
$dest='C:\Chacademi\artifacts\server'
New-Item -ItemType Directory -Path $dest -Force|Out-Null
# Every server plugin module in settings.gradle; this one list drives both the copy and the hash listing.
$projects=@('magic-codex-paper','magic-discovery-paper','creature-spawns-paper','tornado-event-paper','chaca-npc-paper','chaca-portrait-paper')
$built=foreach($project in $projects){
    $jars=@(Get-ChildItem -LiteralPath "$project\build\libs" -File -Filter '*.jar'|Where-Object {$_.Name -notmatch '-sources|-javadoc'})
    if($jars.Count -eq 0){throw "No plugin jar was built for $project"}
    $jars|Copy-Item -Destination $dest -Force
    $jars|ForEach-Object {Join-Path $dest $_.Name}
}
$built|Sort-Object -Unique|ForEach-Object {Get-Item -LiteralPath $_}|ForEach-Object {[pscustomobject]@{Name=$_.Name;Bytes=$_.Length;SHA256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}}|ConvertTo-Json
