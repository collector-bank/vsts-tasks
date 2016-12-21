[cmdletbinding()]
param
(
	[Parameter(Mandatory=$true)][string] $dbName,
	[Parameter(Mandatory=$true)][string] $fileName,
    [Parameter(Mandatory=$false)][string] $OurVerbose = "Continue"
)

$ErrorActionPreference = "Stop"
$VerbosePreference = $OurVerbose

try 
{ 
	$serverName = "localhost"
    
    Write-Host "I am restoring the following database `nName: $($dbName) `nFileName: $($fileName)"

	[void][Reflection.Assembly]::LoadWithPartialName("Microsoft.SqlServer.SMO") 
	[void][Reflection.Assembly]::LoadWithPartialName("Microsoft.SqlServer.SMOExtended") 

	$server = New-Object Microsoft.SqlServer.Management.Smo.Server $serverName

	#create SMO handle to your database
	$DBObject = $Server.Databases[$dbName]

	#check database exists on server
	if ($DBObject)
	{
        Write-Verbose "DBObject exists. I will kill all processes and then kill the database"
		$server.KillAllProcesses($dbName);
		$server.KillDatabase($dbName);
	}
 
	$restore = New-Object Microsoft.SqlServer.Management.Smo.Restore
	$device = New-Object Microsoft.SqlServer.Management.Smo.BackupDeviceItem $fileName, "FILE"
	$restore.Devices.Add($device)

	$filelist = $restore.ReadFileList($server) 
 
	$filestructure = @{}; $datastructure = @{}; $logstructure = @{}
	$logfiles = $filelist | Where-Object {$_.Type -eq "L"}
	$datafiles = $filelist | Where-Object {$_.Type -ne "L"}
 
	# Data Files (if db has filestreams, make sure server has them enabled)
	$defaultdata = $server.DefaultFile
	$defaultlog = $server.DefaultLog
	if ($defaultdata.Length -eq 0) {
		$defaultdata = $server.Information.MasterDBPath
	}
 
	if ($defaultlog.Length -eq 0) {
		$defaultlog = $server.Information.MasterDBLogPath
	}
 
	foreach ($file in $datafiles) {
		$newfilename = Split-Path $($file.PhysicalName) -leaf 
        Write-Verbose "NewFileName: $($newfilename)"

		$datastructure.physical = "$defaultdata$dbName$newfilename"
        Write-Verbose "Physical: $defaultdata$dbName$newfilename"

		$datastructure.logical = $file.LogicalName
        Write-Verbose "Logical: $($file.LogicalName)"
    
		$filestructure.add($file.LogicalName,$datastructure)
	}
 
	# Log Files
	foreach ($file in $logfiles) {
		$newfilename = Split-Path $($file.PhysicalName) -leaf 
        Write-Verbose "NewFileName: $($newfilename)"

		$logstructure.physical = "$defaultlog$dbName$newfilename"
        Write-Verbose "Physical: $defaultlog$dbName$newfilename"

		$logstructure.logical = $file.LogicalName
        Write-Verbose "Logical: $($file.LogicalName)"

		$filestructure.add($file.LogicalName,$logstructure)
	}
 
	# Make sure big restores don't timeout
	$server.ConnectionContext.StatementTimeout = 0
 
	foreach ($file in $filestructure.values) {
		$movefile = New-Object "Microsoft.SqlServer.Management.Smo.RelocateFile" 
		$movefile.LogicalFileName = $file.logical
		$movefile.PhysicalFileName = $file.physical
        Write-Verbose "Moving the following item: `nLogical: $($movefile.LogicalFileName)`nPhysical: $($movefile.PhysicalFileName)"

		$null = $restore.RelocateFiles.Add($movefile)
	}
 
	Write-Host "Restoring $dbName to $serverName" -ForegroundColor Yellow
 
	$percent = [Microsoft.SqlServer.Management.Smo.PercentCompleteEventHandler] { 
		Write-Progress -id 1 -activity "Restoring $dbName to $serverName" -percentcomplete $_.Percent -status ([System.String]::Format("Progress: {0} %", $_.Percent)) 
	}

    $complete = {
        Write-Host "Restore complete!" -ForegroundColor Green
    }

	$restore.add_PercentComplete($percent)
	$restore.PercentCompleteNotification = 1
	$restore.add_Complete($complete)
	$restore.ReplaceDatabase = $true
	$restore.Database = $dbName
	$restore.Action = "Database"
	$restore.NoRecovery = $false
 
	Write-Progress -id 1 -activity "Restoring $dbName to $serverName" -percentcomplete 0 -status ([System.String]::Format("Progress: {0} %", 0))
	$restore.sqlrestore($serverName)
	Write-Progress -id 1 -activity "Restoring $dbName to $serverName" -status "Complete" -Completed
}
catch 
{
	$exception = $_.Exception.InnerException; 
	throw (Get-LocalizedString -Key "Failed to restore database with exception: {0}" -ArgumentList $exception)
}