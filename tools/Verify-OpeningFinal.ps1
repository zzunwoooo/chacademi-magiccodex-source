param([string]$LogDirectory = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (!$LogDirectory) { $LogDirectory = Join-Path $root 'verification/opening-final' }
New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME = 'C:\Chacademi\tools\gradle-cache'
$manifest = Get-Content -Raw (Join-Path $root 'build-support/opening-final-dependencies.json') | ConvertFrom-Json
foreach ($item in $manifest) {
    $file = Join-Path $root $item.path
    if (!(Test-Path -LiteralPath $file) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $item.sha256) {
        throw "Missing or mismatched private dependency: $($item.path). Restore the hash-matched host baseline; do not commit the binary."
    }
}
$jobs = @(
    @{Name='server';Directory='.';Tasks=@('verifyServer','assembleServer')},
    @{Name='codex-client';Directory='magic-codex-fabric';Tasks=@('build')},
    @{Name='story-plugin';Directory='chacademy-story/plugin';Tasks=@('build')},
    @{Name='story-client';Directory='chacademy-story/mod';Tasks=@('build')},
    @{Name='vfx';Directory='portable-vfx-runtime';Tasks=@('build')},
    @{Name='motion-plugin';Directory='chacademy-motion/chacademy-motion-plugin';Tasks=@('build')},
    @{Name='motion-client';Directory='chacademy-motion/chacademy-motion-mod';Tasks=@('build')},
    @{Name='chat-layout';Directory='client-mods/chacademi-chat-layout';Tasks=@('build')},
    @{Name='npc-model';Directory='client-mods/chacademi-npc-model';Tasks=@('build')}
)
$results = @()
foreach ($job in $jobs) {
    $log = Join-Path $LogDirectory "$($job.Name).log"
    & (Join-Path $root 'gradlew.bat') -p (Join-Path $root $job.Directory) --offline --no-daemon --console=plain --max-workers=1 --continue '-Dorg.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8' -I (Join-Path $root 'build-support/bounded-tests.init.gradle') @($job.Tasks) *> $log
    $results += [pscustomobject]@{Project=$job.Name;ExitCode=$LASTEXITCODE;Log=$log}
    $results | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $LogDirectory 'results.json')
}
$results | Format-Table
if (@($results | Where-Object ExitCode -ne 0).Count) { throw 'One or more full build/test groups failed; inspect results.json.' }
