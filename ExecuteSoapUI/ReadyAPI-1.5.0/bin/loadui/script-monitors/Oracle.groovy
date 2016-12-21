//
// Copyright 2014 SmartBear Software
//

/**
 * Oracle DB Server Monitor using JDBC to poll performance counters from a remote host.
 *
 * @name Oracle
 * @id com.eviware.loadui.monitors.OracleDB
 * @category databases
 * @help http://loadui.org/Server-monitoring/oracle-database.html
 * @module jdbc
 */

import groovy.sql.Sql

monitorName = createProperty('monitorName', String, null)

port.value = 1521

/*** SQL QUERIES ***/

createProperty('RAC', Boolean, false); //Means that the SQL Queries will use GV$Table instead of V$Table.

miscQueries = []
sessionQueries = []
missRateQueries = []
sgaMemoryQueries = []
callRateQueries = []
eventWaitsQueries = []
logicalIoQueries = []
logicalIoQueries = []
physicalIoQueries = []
counterGroups = []

/* QUERIES FOR THE MISC COUNTERS*/
def setMiscQueries = {

    miscQueries = []
    miscQueries << "select 'Recursive Calls Rate (%)' as name, (rcv.value/(rcv.value+usr.value))*100 as value from " + prefix + "sysstat rcv, " + prefix + "sysstat usr where rcv.name='recursive calls' and usr.name='user calls'"
    miscQueries << "select 'Cpu Parse Overhead (%)', (prs.value/(prs.value+exe.value))*100 from " + prefix + "sysstat prs, " + prefix + "sysstat exe where prs.name like 'parse count (hard)' and exe.name='execute count'"
    miscQueries << "select 'Free List Contention (%)', (sum(decode(w.class,'free list',count,0))/(sum(decode(name,'db block gets',value,0)) + sum(decode(name,'consistent gets',value,0))))*100    from " + prefix + "waitstat w, " + prefix + "sysstat"
    miscQueries << "select 'Chained Fetch Ratio (%)', (cont.value/(scn.value+rid.value))*100 from " + prefix + "sysstat cont, " + prefix + "sysstat scn, " + prefix + "sysstat rid where cont.name= 'table fetch continued row' and scn.name= 'table scan rows gotten' and rid.name= 'table fetch by rowid'"
    miscQueries << "select 'Cursor Authentications', value from " + prefix + "sysstat where name='cursor authentications'"
    miscQueries << "select 'OpenCursors', count(1) from " + prefix + "sesstat a, " + prefix + "statname b, " + prefix + "session s where a.statistic# = b.statistic#  and s.sid=a.sid and b.name = 'opened cursors current'"
    miscQueries << "select 'LibCache, Get Hit rate (%)' as name, sum(gethits)/sum(gets)*100 value from " + prefix + "librarycache"
    miscQueries << "select 'LibCache, Pin Hit rate (%)' as name, sum(pinhits)/sum(pins)*100 as value from " + prefix + "librarycache"

}

/* QUERIES FOR THE SESSION COUNTERS */
def setSessionQueries = {

    sessionQueries = []
    sessionQueries << "SELECT INITCAP(STATUS) AS name, COUNT(1) AS value FROM " + prefix + "Session WHERE UserName IS NOT NULL GROUP BY STATUS UNION SELECT 'System', COUNT(1) FROM " + prefix + "Session WHERE UserName IS NULL"
}

/* QUERIES FOR THE MISS RATE COUNTERS */
def setMissRateQueries = {

    missRateQueries = []
    missRateQueries << "SELECT 'Latch - Willing to wait' as name, SUM(MISSES)/SUM(GETS)*100 as value FROM " + prefix + "LATCH"
    missRateQueries << "SELECT 'Latch - Immediate', SUM(IMMEDIATE_MISSES)/SUM(IMMEDIATE_GETS)*100 FROM " + prefix + "LATCH"
    missRateQueries << "SELECT 'SQL Area (Shared Pool Reloads)', sum(reloads)/sum(pins)*100 from " + prefix + "librarycache where namespace in ('SQL AREA','TABLE/PROCEDURE','BODY','TRIGGER')"
    missRateQueries << "SELECT 'Buffer Cache', (pr.value/(bg.value+cg.value))*100 from " + prefix + "sysstat pr, " + prefix + "sysstat bg, " + prefix + "sysstat cg where pr.name='physical reads' and bg.name='db block gets' and cg.name='consistent gets'"

}

/* SGA Memory */
def setSgaMemoryQueries = {

    sgaMemoryQueries = []
    sgaMemoryQueries << "select pool as name, sum(bytes) as value from " + prefix + "sgastat where pool in ('large pool','java pool', 'shared pool') and name = 'free memory' group by pool"
    sgaMemoryQueries << "select name, sum(bytes) FROM " + prefix + "sgastat WHERE pool is null AND name in ('fixed_sga', 'log_buffer', 'db_block_buffers') GROUP BY name"

}

/* Call Rates */
def setCallRateQueries = {

    callRateQueries = []
    callRateQueries << "select name, value from " + prefix + "sysstat where name in ('parse count (total)', 'parse count (hard)', 'execute count', 'user rollbacks', 'user commits' )"

}

/* Event Waits */
def setEventWaitsQueries = {

    eventWaitsQueries = []
    eventWaitsQueries << "select event as name, TIME_WAITED_MICRO as value from " + prefix + "SYSTEM_EVENT where event in ('buffer busy waits','direct path read')"
    eventWaitsQueries << "select 'Log File write', SUM(TIME_WAITED_MICRO) from " + prefix + "SYSTEM_EVENT where event in ('log file single write','log file parallel write')"
    eventWaitsQueries << "select 'SQL*Net', SUM(TIME_WAITED_MICRO) from " + prefix + "SYSTEM_EVENT where event like 'SQL*Net%' "
    eventWaitsQueries << "select 'DB File IO', SUM(TIME_WAITED_MICRO) from " + prefix + "SYSTEM_EVENT where event like 'db file%' "
    eventWaitsQueries << "select 'Control File IO', SUM(TIME_WAITED_MICRO) from " + prefix + "SYSTEM_EVENT where event like 'control file%' "

}

/* Logical IO */
def setLogicalIoQueries = {

    logicalIoQueries = []
    logicalIoQueries << "select name, value from " + prefix + "sysstat where name in ('db block changes', 'db block gets', 'consistent gets', 'user rollbacks' )"

}

/* Physical IO */
def setPhysicalIoQueries = {

    physicalIoQueries = []
    physicalIoQueries << "select name, value from " + prefix + "sysstat where name in ('physical reads', 'physical writes', 'redo writes' )"

}

prefix = '--->PREFIX NOT SET<---'

/*** Groovy code ***/
def getPrefix() {
    if (RAC.value) {
        'GV$'
    } else {
        'V$'
    }
}

try {
    //addToClassPath( new File('ext/ojdbc6.jar').toURI().toURL() )
    driver = Class.forName('oracle.jdbc.driver.OracleDriver', true, Thread.currentThread().getContextClassLoader()).newInstance()
} catch (e) {
    warn("Could not find Oracle JDBC Driver in ext folder!")
}

createProperty('service', String, '') //orcl.eviware.local

gSql = null

def setCounterGroups = {

    counterGroups = [
            'Sessions'             : [queries: sessionQueries, derivedCounters: ['Active (%)': {
                m = it.collectEntries {
                    [it.name, it.value]
                }
                [NAME: 'Active (%)', VALUE: m.Active && m.Inactive ? 100 * m.Active / (m.Active + m.Inactive) : null]
            }
            ]
            ],
            'Misc.'                : [queries: miscQueries],
            'Miss rates'           : [queries: missRateQueries],
            'SGA Memory'           : [queries: sgaMemoryQueries],
            'Call rates (per sec)' : [queries: callRateQueries, transform: 'PER_SECOND'],
            'Event Waits (per sec)': [queries: eventWaitsQueries, transform: 'PER_SECOND'],
            'Logical IO (per sec)' : [queries: logicalIoQueries, transform: 'PER_SECOND'],
            'Physical IO (per sec)': [queries: physicalIoQueries, transform: 'PER_SECOND']
    ]
}

def updateQueries = {

    prefix = getPrefix()
    setMiscQueries()
    setMissRateQueries()
    setSgaMemoryQueries()
    setCallRateQueries()
    setEventWaitsQueries()
    setLogicalIoQueries()
    setPhysicalIoQueries()
    setSessionQueries()
    setCounterGroups()
}

updateQueries()

def lock = new Object()

muteWarnings = false

validateConnection = {
    if (!gSql?.connection?.isValid(60)) {
        try {
            gSql = new Sql(driver.connect('jdbc:oracle:thin:@//' + serverHost + ':' + port.value + '/' + service.value, jdbc.credentials))
            muteWarnings = false
        } catch (java.sql.SQLException e) {
            if (!muteWarnings) {
                warn('Oracle monitor lost connectivity to ' + serverHost + '.')
            }
            muteWarnings = true
            throw e
        }
    }
}

monVar = null

testConnection = {
    try {
        connect()
    }
    catch (java.sql.SQLException e) {
        if (e.errorCode == 942) {
            warn('User most likely does not have access to the view\nSee the documentation for more info:\nhttp://www.loadui.org/Server-monitoring/oracle-database.html')
        }
        throw e
    }
}

connect = {
    synchronized (lock) {
        updateQueries()
        validateConnection()
        counterGroups.each {
            rows = gSql.rows(it.value.queries.join(' UNION ALL '))
            it.value?.derivedCounters.each { rows << it.value(rows) }
            counterGroup = jdbc.registerCounterGroupFromTable(it.key, rows)
            if (it.value.transform) {
                counterGroup = transformCounters(counterGroup, it.value.transform)
            }
            it.value.put('counterGroup', counterGroup)
        }
    }
}

disconnect = {
    synchronized (lock) {
        gSql.close()
    }
}

onUpdate = {
    synchronized (lock) {
        validateConnection()
        try {
            counterGroups.each {
                rows = gSql.rows(it.value.queries.join(' UNION ALL '))
                it.value?.derivedCounters.each { rows << it.value(rows) }
                jdbc.updateCountersFromTable(it.value.counterGroup, rows)
            }
        } catch (java.sql.SQLException e) {
            validateConnection()
        }
    }
}

settings(label: 'General') {
    jdbc.getSettingsPageEntries().findAll { !it.containsKey('action') }.each { node(it) }
    property(property: service, label: 'Service name')
    property(property: RAC, label: 'RAC Support')
    jdbc.getSettingsPageEntries().findAll { it.containsKey('action') }.each { node(it) }
}

defaultStatistics << statistic('Miss rates', 'Buffer Cache')
defaultStatistics << statistic('Physical IO (per sec)', 'physical reads')
defaultStatistics << statistic('Physical IO (per sec)', 'physical writes')
