[cmdletbinding()]
param
(
	[Parameter(Mandatory=$true)][string] $nuspecPath,
	[Parameter(Mandatory=$true)][string] $versionSuffix
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


[xml]$nuspecXML = Get-Content $nuspecPath
$newVersion = $nuspecXML.package.metadata.version + $versionSuffix

$nuspecXML.package.metadata.version = $newVersion
$nuspecXML.Save($nuspecPath)

Write-Host "Version: $newVersion"

"##vso[task.setvariable variable=NuspecVersion;]$newVersion"


# Apply the version to the assembly property files
$files = gci $Env:BUILD_SOURCESDIRECTORY -recurse -include "*Properties*","My Project" | 
    ?{ $_.PSIsContainer } | 
    foreach { gci -Path $_.FullName -Recurse -include AssemblyInfo.* }

if($files)
{
    Write-Host "Will apply $newVersion to $($files.count) files."

    foreach ($file in $files)
	{
        $filecontent = Get-Content($file)
        attrib $file -r
		$newAssemblyInformationalVersion = 'AssemblyInformationalVersion("' + $newVersion
        $filecontent -replace 'AssemblyInformationalVersion\("\d+\.\d+\.\d+\.\d+', $newAssemblyInformationalVersion | Out-File $file
        Write-Host "$file.FullName - version applied"
    }
}
else
{
    Write-Host "Found no files."
}