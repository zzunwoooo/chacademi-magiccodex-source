param([switch]$NoBrowser)
$ErrorActionPreference='Stop'
$editorDir=$PSScriptRoot
$runtime=Join-Path $editorDir 'data/runtime.json'
$current=$null
if(Test-Path -LiteralPath $runtime){
    try{
        $candidate=Get-Content -LiteralPath $runtime -Raw | ConvertFrom-Json
        if($candidate.url -notmatch '^http://127\.0\.0\.1:\d+/$'){throw 'Invalid local address'}
        $health=Invoke-RestMethod -Uri ($candidate.url+'api/health') -TimeoutSec 2
        if($health.app -eq 'chacademia-discovery-editor' -and $health.datasetId -eq $candidate.datasetId){$current=$candidate}
    }catch{}
}
if(-not $current){
    $python='C:/Chacademi/tools/python/python.exe'
    if(-not (Test-Path -LiteralPath $python)){throw 'Python runtime not found: C:/Chacademi/tools/python/python.exe'}
    $script=Join-Path $editorDir 'server.py'
    $process=Start-Process -FilePath $python -ArgumentList @('"'+$script+'"') -WorkingDirectory $editorDir -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $editorDir 'server.log') -RedirectStandardError (Join-Path $editorDir 'server-error.log')
    for($attempt=0;$attempt -lt 40;$attempt++){
        Start-Sleep -Milliseconds 150
        if(Test-Path -LiteralPath $runtime){
            try{$candidate=Get-Content -LiteralPath $runtime -Raw | ConvertFrom-Json;if($candidate.pid -eq $process.Id){$current=$candidate;break}}catch{}
        }
        if($process.HasExited){throw 'Editor could not start. See server-error.log.'}
    }
    if(-not $current){throw 'Editor startup timed out.'}
}
if(-not $NoBrowser){Start-Process $current.url}
Write-Output $current.url
