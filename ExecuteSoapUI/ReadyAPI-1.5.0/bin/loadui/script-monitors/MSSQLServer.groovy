//
// Copyright 2014 SmartBear Software
//

/**
 * Microsoft SQL Server monitor using WMI to poll Windows Performance Counters from a remote host.
 *
 * @id com.eviware.loadui.monitors.MSSQL
 * @name MS SQL Server
 * @category databases
 * @help http://loadui.org/Server-monitoring/mssql-server-monitor.html
 * @module typeperf
 */

monitorName = createProperty('monitorName', String, null)

['Access Methods', 'Databases', 'General Statistics', 'SQL Statistics', 'SQL Errors', 'Transactions', 'Wait Statistics', 'Resource Pool Stats', 'Buffer Manager', 'Memory Manager', 'Latches', 'Locks'].each {
    typeperf.manages("SQLServer:$it")
}

connect = { typeperf.connect() }
disconnect = { typeperf.disconnect() }

settings(label: "Credentials") {
    label(label: "This monitor uses your Windows credentials. Please make sure that the user you are running loadUI as has the needed permissions to access the performance counter data of the target server.\r\n\r\n")
    def connectionStatus = "Untested..."
    action(label: "Test Connection", async: false, action: {
        connectionStatus = typeperf.testConnection() ? "Success!" : "Failure!"
    }, status: { connectionStatus })
}

defaultStatistics << statistic('SQLServer:Resource Pool Stats', 'CPU usage %', 'default')
defaultStatistics << statistic('SQLServer:SQL Statistics', 'SQL Compilations/sec')
defaultStatistics << statistic('SQLServer:SQL Statistics', 'Batch Requests/sec')
