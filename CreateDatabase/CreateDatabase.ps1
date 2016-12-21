[cmdletbinding()]
param
(
	[Parameter(Mandatory=$true)][string] $dbName
)

try { 
	$serverName = "localhost"
 
	[void][Reflection.Assembly]::LoadWithPartialName("Microsoft.SqlServer.SMO") 
	[void][Reflection.Assembly]::LoadWithPartialName("Microsoft.SqlServer.SMOExtended") 

	$server = New-Object Microsoft.SqlServer.Management.Smo.Server $serverName

	$db = New-Object Microsoft.SqlServer.Management.Smo.Database($server, $dbName)
	$db.Create()

	Write-Host "Database $dbName created!"

}catch {
	$exception = $_.Exception.InnerException; 
	throw (Get-LocalizedString -Key "Failed to create database with exception: {0}" -ArgumentList $exception)
}