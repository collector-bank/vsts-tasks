try{
	Get-MsmqQueue -QueueType Private | Clear-MsmqQueue
}
catch{
	$exception = $_.Exception.InnerException; 
	throw (Get-LocalizedString -Key "Failed to purge queues with exception: {0}" -ArgumentList $exception)
}
