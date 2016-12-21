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

	#create SMO handle to your database
	$DBObject = $Server.Databases[$dbName]

	#check database exists on server
	if ($DBObject)
	{
		Write-Host "Removing $dbName"
		$server.KillAllProcesses($dbName);
		$server.KillDatabase($dbName);
	}
	else
	{
		Write-Host "Could not find a database with name $dbName"
	}
 
}catch {
	$exception = $_.Exception.InnerException; 
	throw (Get-LocalizedString -Key "Failed to remove database with exception: {0}" -ArgumentList $exception)
}