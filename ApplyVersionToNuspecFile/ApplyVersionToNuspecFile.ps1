[cmdletbinding()]
param
(
	[Parameter(Mandatory=$true)][string] $versionFilePath,
	[Parameter(Mandatory=$true)][string] $nuspecFilePath
)

if (-not (Test-Path $versionFilePath))
{
    Write-Host "File does not exist: $versionFilePath"
    exit 1
}

if (-not (Test-Path $nuspecFilePath))
{
    Write-Host "File does not exist: $nuspecFilePath"
    exit 1
}

$newVersion =  [string](Get-Content $versionFilePath).Trim()

[xml]$nuspecXML = Get-Content $nuspecFilePath

$nuspecXML.package.metadata.version = $newVersion
$nuspecXML.Save($nuspecFilePath)

Write-Host "Updated $nuspecFilePath with version $newVersion"
