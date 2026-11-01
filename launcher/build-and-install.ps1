param([string]$TvHost=$env:TV_HOST,[switch]$SetHome,[switch]$EnableAssist,[switch]$WifiNames,[string]$Config)
$ErrorActionPreference='Stop'
$repo=Split-Path -Parent $PSScriptRoot
$arguments=@((Join-Path $repo 'tools/install.py'))
if ($TvHost) { $arguments+=@('--device',$TvHost) }
if ($SetHome) { $arguments+='--set-home' }
if ($EnableAssist) { $arguments+='--enable-assist' }
if ($WifiNames) { $arguments+='--wifi-names' }
if ($Config) { $arguments+=@('--config',$Config) }
& python @arguments
if ($LASTEXITCODE -ne 0) { throw 'Installation stopped. See the message above; original launcher remains enabled.' }
