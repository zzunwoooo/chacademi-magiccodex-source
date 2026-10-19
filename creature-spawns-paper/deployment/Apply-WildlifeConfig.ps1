#requires -Version 7.0
[CmdletBinding(SupportsShouldProcess)]
param([string]$NetworkRoot='C:\Chacademi\network')
$ErrorActionPreference='Stop'
$utf8=[Text.UTF8Encoding]::new($false)
$state=Get-Content -Raw -LiteralPath (Join-Path $NetworkRoot 'runtime\state.json')|ConvertFrom-Json
$wild=@($state.servers|Where-Object id -eq 'wild')
if($wild.Count -ne 1 -or $wild[0].alive){throw 'Wild must be stopped by the coordinated deployment owner first.'}
if($wild[0].pid -and (Get-Process -Id $wild[0].pid -ErrorAction SilentlyContinue)){throw 'Recorded wild process is still running.'}
$plugins=Join-Path $NetworkRoot 'wild\plugins'
$jarName='chacademia-wildlife-0.3.1-chunkcap.1.jar'
$preparedJar=Join-Path $PSScriptRoot $jarName
$installedJar=Join-Path $plugins $jarName
if(!(Test-Path -LiteralPath $preparedJar) -or !(Test-Path -LiteralPath $installedJar)){throw 'Install the bundled protected Wildlife JAR before changing spawn gates.'}
if((Get-FileHash -LiteralPath $preparedJar).Hash -ne (Get-FileHash -LiteralPath $installedJar).Hash){throw 'Installed protected JAR hash mismatch.'}
$wildlifeJars=@(Get-ChildItem -LiteralPath $plugins -Filter 'chacademia-wildlife-*.jar' -File)
if($wildlifeJars.Count -ne 1 -or $wildlifeJars[0].Name -ne $jarName){throw 'Remove the old Wildlife JAR from plugins during the coordinated deployment.'}
$config=Join-Path $plugins 'ChAcademiaWildlife\config.yml'
$random=Join-Path $plugins 'MythicMobs\RandomSpawns\chacademia_wildlife.yml'
$spawning=Join-Path $plugins 'MythicMobs\config\config-spawning.yml'
$c=Get-Content -Raw -Encoding UTF8 -LiteralPath $config|ConvertFrom-Json -AsHashtable
if($c['area-cap'] -ne 24){throw 'Existing area-cap changed; review before patching.'}
$c['chunk-cap']=6
$r=Get-Content -Raw -Encoding UTF8 -LiteralPath $random|ConvertFrom-Json -AsHashtable
$rules=@($r.Keys|Where-Object {$_ -like 'CA_Natural_*'})
if($rules.Count -ne 138){throw 'Unexpected natural spawn pack; review before patching.'}
foreach($key in $rules){
 if($r[$key].Action -ne 'ADD' -or !($r[$key].Conditions -match '^cahabitat\{')){throw 'Natural rule is missing Wildlife protection.'}
 $r[$key]['MaxLocalMobs']=0
}
$s=Get-Content -Raw -Encoding UTF8 -LiteralPath $spawning
if(([regex]::Matches($s,'(?m)^    SpawningLimit: (48|0)\s*$')).Count -ne 1){throw 'Unexpected global spawning limit; review before patching.'}
if(([regex]::Matches($s,'(?m)^    LocalGroupMultiplier: (0\.1|1\.33)\s*$')).Count -ne 1){throw 'Unexpected group multiplier; review before patching.'}
$s=[regex]::Replace($s,'(?m)^    SpawningLimit: (48|0)[ \t]*\r?$','    SpawningLimit: 0')
$s=[regex]::Replace($s,'(?m)^    LocalGroupMultiplier: (0\.1|1\.33)[ \t]*\r?$','    LocalGroupMultiplier: 1.33')
if(!$PSCmdlet.ShouldProcess($plugins,'Apply Wildlife chunk 6 / area 24 protections and paired Mythic ADD gate patch')){return}
$backup=Join-Path (Split-Path $NetworkRoot) ('backups\wildlife-chunkcap-config-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup|Out-Null
Copy-Item -LiteralPath $config -Destination (Join-Path $backup 'wildlife-config-before.json')
Copy-Item -LiteralPath $random -Destination (Join-Path $backup 'randomspawns-before.json')
Copy-Item -LiteralPath $spawning -Destination (Join-Path $backup 'mythic-spawning-before.yml')
[IO.File]::WriteAllText($config,($c|ConvertTo-Json -Depth 12)+[Environment]::NewLine,$utf8)
[IO.File]::WriteAllText($random,($r|ConvertTo-Json -Depth 12)+[Environment]::NewLine,$utf8)
[IO.File]::WriteAllText($spawning,$s,$utf8)
Write-Output "Config patch applied. Backup: $backup"
Write-Output 'Start wild only through the coordinated deployment owner, then run wildlifeadmin status.'
