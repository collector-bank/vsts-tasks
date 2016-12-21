[cmdletbinding()]
param
(
	[Parameter(Mandatory=$true)][string] $project_xml,
	[Parameter(Mandatory=$true)][string] $soap_ui_licence_file,
	[Parameter(Mandatory=$true)][string] $soap_ui_dat_file
)

import-module "Microsoft.TeamFoundation.DistributedTask.Task.Internal"
import-module "Microsoft.TeamFoundation.DistributedTask.Task.Common"

$soapUiPath = "C:\Users\$($env:UserName)\.soapui"

if (!(Test-Path $soapUiPath)){
	New-Item $soapUiPath -ItemType Directory
}

Copy-Item $soap_ui_licence_file $soapUiPath
Copy-Item $soap_ui_dat_file $soapUiPath

$pathToTaskDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "My invoication path: $pathToTaskDirectory"

$soap_ui_bat_path = "$pathToTaskDirectory\ReadyAPI-1.5.0\bin\testrunner.bat"
& $soap_ui_bat_path -R "TestCase Report" -E "Default environment" $project_xml -r

Write-Host "Exit code: $LastExitCode" -Fore Yellow

if ($LastExitCode -ne 0){
	Write-Host 'Testrun failed!' -Fore Red
	Throw "Build cancelled"
}
else{
	Write-Host 'No errors' -Fore Green
}
	

