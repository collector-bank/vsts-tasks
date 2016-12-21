//
// Copyright 2014 SmartBear Software
//

/**
 * Windows Server Monitor using WMI to poll Windows Performance Counters from a remote host.
 *
 * @id com.eviware.loadui.monitors.Windows
 * @category operating systems
 * @help http://loadui.org/Server-monitoring/windows-server-monitor.html
 * @module typeperf
 */

['System', 'Processor', 'Memory', 'PhysicalDisk', 'LogicalDisk', 'Network Interface', 'Paging File'].each {
    typeperf.manages(it)
}

monitorName = createProperty('monitorName', String, null)

connect = { typeperf.connect() }
disconnect = { typeperf.disconnect() }

settings(label: "Credentials") {
    label(label: "This monitor uses your Windows credentials. Please make sure that the user you are running loadUI as has the needed permissions to access the performance counter data of the target server.\r\n\r\n")
    def connectionStatus = "Untested..."
    action(label: "Test Connection", async: false, action: {
        connectionStatus = typeperf.testConnection() ? "Success!" : "Failure!"
    }, status: { connectionStatus })
}

defaultStatistics << statistic('Processor', '% Processor Time', '_Total')
defaultStatistics << statistic('Memory', 'Available MBytes')
