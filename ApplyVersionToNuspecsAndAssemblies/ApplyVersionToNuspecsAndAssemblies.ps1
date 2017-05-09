[cmdletbinding()]
param
(
	[Parameter(Mandatory=$true)][string] $version
)

if (-not $Env:BUILD_SOURCESDIRECTORY)
{
    Write-Host ('$Env:BUILD_SOURCESDIRECTORY environment variable is missing.')
    exit 1
}
if (-not (Test-Path $Env:BUILD_SOURCESDIRECTORY))
{
    Write-Host "Env:BUILD_SOURCESDIRECTORY does not exist: $Env:BUILD_SOURCESDIRECTORY"
    exit 1
}

Write-Host "BUILD_SOURCESDIRECTORY: $Env:BUILD_SOURCESDIRECTORY"

Write-Host "Version: $version"

# Apply the version to the nuspec files
$nuspecFiles = gci $Env:BUILD_SOURCESDIRECTORY -recurse | where { $_.extension -eq ".nuspec" } | foreach { gci -Path $_.FullName }

if($nuspecFiles)
{
    Write-Host "Will apply version $version to $($nuspecFiles.count) nuspec files."

    foreach ($nuspecFile in $nuspecFiles)
	{
		[xml]$nuspecXML = Get-Content $nuspecFile
		$nuspecXML.package.metadata.version = $version
		$nuspecXML.Save($nuspecFile)
        Write-Host "$nuspecFile - version applied"
    }
}
else
{
    Write-Host "No nuspec files found."
}


# Apply the version to the assembly property files
$assemblyInfoFiles = gci $Env:BUILD_SOURCESDIRECTORY -recurse -include "*Properties*","My Project" | foreach { gci -Path $_.FullName -Recurse -include AssemblyInfo.* }

if($assemblyInfoFiles)
{
    Write-Host "Will apply version $version to $($assemblyInfoFiles.count) files."

    foreach ($assemblyInfoFile in $assemblyInfoFiles)
	{
        $filecontent = Get-Content($assemblyInfoFile)
        attrib $assemblyInfoFile -r
		$newAssemblyInformationalVersion = 'AssemblyInformationalVersion("' + $version
        $filecontent -replace 'AssemblyInformationalVersion\("\d+\.\d+\.\d+\.\d+', $newAssemblyInformationalVersion | Out-File $assemblyInfoFile
        Write-Host "$assemblyInfoFile - version applied"
    }
}
else
{
    Write-Host "No AssemblyInfo files found."
}