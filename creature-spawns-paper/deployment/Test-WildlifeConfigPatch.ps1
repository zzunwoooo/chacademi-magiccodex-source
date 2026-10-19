#requires -Version 7.0
param([Parameter(Mandatory)][string]$BundleRoot,[string]$SourceNetwork='C:\Chacademi\network')
$ErrorActionPreference='Stop'
$patch=Join-Path $BundleRoot 'Apply-WildlifeConfig.ps1'
$jar='chacademia-wildlife-0.3.1-chunkcap.1.jar'
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('wildlife-deploy-test-'+[guid]::NewGuid().ToString('N'))
$plugins=Join-Path $fixture 'wild\plugins'
$utf8=[Text.UTF8Encoding]::new($false)
foreach($dir in @('runtime','wild\plugins\ChAcademiaWildlife','wild\plugins\MythicMobs\RandomSpawns','wild\plugins\MythicMobs\config')){
 New-Item -ItemType Directory -Path (Join-Path $fixture $dir) -Force|Out-Null
}
$names=@('ChAcademiaWildlife\config.yml','MythicMobs\RandomSpawns\chacademia_wildlife.yml','MythicMobs\config\config-spawning.yml')
foreach($name in $names){Copy-Item -LiteralPath (Join-Path $SourceNetwork "wild\plugins\$name") -Destination (Join-Path $plugins $name)}
Copy-Item -LiteralPath (Join-Path $BundleRoot $jar) -Destination (Join-Path $plugins $jar)
function State($alive){[IO.File]::WriteAllText((Join-Path $fixture 'runtime\state.json'),(@{servers=@(@{id='wild';alive=$alive;pid=0})}|ConvertTo-Json -Depth 4),$utf8)}
function Reject($expected){
 try{& $patch -NetworkRoot $fixture -Confirm:$false|Out-Null;throw 'Guard unexpectedly accepted'}
 catch{if($_.Exception.Message -notlike "*$expected*"){throw};Write-Output "PASS rejection: $expected"}
}
State $true;Reject 'must be stopped'
State $false
[IO.File]::WriteAllText((Join-Path $plugins $jar),'wrong')
Reject 'hash mismatch'
Copy-Item -LiteralPath (Join-Path $BundleRoot $jar) -Destination (Join-Path $plugins $jar) -Force
$old=Join-Path $plugins 'chacademia-wildlife-0.3.0.jar'
[IO.File]::WriteAllText($old,'duplicate')
Reject 'old Wildlife JAR'
Remove-Item -LiteralPath $old
$before=@($names|ForEach-Object {(Get-FileHash -LiteralPath (Join-Path $plugins $_)).Hash})
& $patch -NetworkRoot $fixture -WhatIf
$after=@($names|ForEach-Object {(Get-FileHash -LiteralPath (Join-Path $plugins $_)).Hash})
if(Compare-Object $before $after){throw 'WhatIf changed configs'}
Write-Output 'PASS WhatIf preserves config hashes'
$original=Get-Content -Raw -Encoding UTF8 (Join-Path $plugins $names[1])|ConvertFrom-Json -AsHashtable
& $patch -NetworkRoot $fixture -Confirm:$false
$c=Get-Content -Raw -Encoding UTF8 (Join-Path $plugins $names[0])|ConvertFrom-Json -AsHashtable
$r=Get-Content -Raw -Encoding UTF8 (Join-Path $plugins $names[1])|ConvertFrom-Json -AsHashtable
$s=Get-Content -Raw -Encoding UTF8 (Join-Path $plugins $names[2])
if($c['chunk-cap'] -ne 6 -or $c['area-cap'] -ne 24){throw 'Wrong Wildlife caps'}
$keys=@($r.Keys|Where-Object {$_ -like 'CA_Natural_*'})
if($keys.Count -ne 138){throw 'Wrong rule count'}
foreach($key in $keys){if($r[$key]['MaxLocalMobs'] -ne 0){throw 'Local gate remains'};[void]$r[$key].Remove('MaxLocalMobs')}
if(($r|ConvertTo-Json -Depth 12 -Compress) -ne ($original|ConvertTo-Json -Depth 12 -Compress)){throw 'Other spawn rules changed'}
if($s -notmatch '(?m)^    SpawningLimit: 0\s*$' -or $s -notmatch '(?m)^    LocalGroupMultiplier: 1\.33\s*$' -or $s -notmatch '(?m)^    LocalSpawningLimit: 24\s*$'){throw 'Wrong Mythic gates'}
Write-Output 'PASS apply: chunk 6, area 24, global 0, multiplier 1.33, 138 local overrides; all other spawn rules preserved'
Write-Output "Deployment fixture: $fixture"
