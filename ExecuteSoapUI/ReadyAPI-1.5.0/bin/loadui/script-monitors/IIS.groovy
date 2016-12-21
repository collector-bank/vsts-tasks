//
// Copyright 2014 SmartBear Software
//

/**
 * Microsoft Internet Information Services monitor using WMI to poll Windows Performance Counters from a remote host.
 *
 * @id com.eviware.loadui.monitors.IIS
 * @category web servers
 * @help http://loadui.org/Server-monitoring/iis-server-monitor.html
 * @module typeperf
 */

['Web Service', 'Web Service Cache'].each { typeperf.manages(it) }

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

defaultStatistics << statistic('Web Service', 'Current Connections', '_Total')
defaultStatistics << statistic('Web Service Cache', 'Kernel: URI Cache Hits')
