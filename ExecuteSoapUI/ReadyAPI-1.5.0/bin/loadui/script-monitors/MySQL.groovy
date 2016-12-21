//
// Copyright 2014 SmartBear Software
//

/**
 * MySQL Server Monitor using JDBC to poll performance counters from a remote host.
 *
 * @id com.eviware.loadui.monitors.MySQL
 * @category databases
 * @help http://loadui.org/Server-monitoring/mysql.html
 * @module jdbc
 */

import groovy.sql.Sql

monitorName = createProperty('monitorName', String, null)

port.value = 3306

def statusVariables = [
        'Connections'  : ['Aborted_clients', 'Threads_connected', 'Threads_cached', 'Threads_running', 'Slow_launch_threads'],
        'Command count': ['Flush_commands', 'Handler_commit', 'Handler_delete', 'Handler_rollback', 'Handler_savepoint', 'Handler_savepoint_rollback', 'Handler_update', 'Handler_write'],
        'InnoDB'       : ['Innodb_buffer_pool_wait_free', 'Innodb_pages_created', 'Innodb_pages_read', 'Innodb_pages_written', 'Innodb_row_lock_current_waits', 'Innodb_row_lock_time_avg', 'Innodb_row_lock_waits'],
        'IO'           : ['Bytes_received', 'Bytes_sent', 'Open_files'],
        'Key rates'    : ['Key_blocks_not_flushed', 'Key_blocks_unused', 'Key_blocks_used', 'Key_read_requests', 'Key_reads', 'Key_write_requests', 'Key_writes'],
        'Query cache'  : ['Qcache_free_blocks', 'Qcache_total_blocks', 'Qcache_hits', 'Qcache_inserts', 'Qcache_lowmem_prunes', 'Qcache_not_cached', 'Qcache_queries_in_cache'],
        'Query count'  : ['Queries', 'Slow_queries', 'Select_full_join', 'Select_range', 'Select_range_check'],
        'Read rates'   : ['Handler_read_first', 'Handler_read_key', 'Handler_read_next', 'Handler_read_prev', 'Handler_read_rnd', 'Handler_read_rnd_next'],
        'Sort count'   : ['Sort_merge_passes', 'Sort_range', 'Sort_rows', 'Sort_scan'],
        'Tables'       : ['Open_tables', 'Opened_tables', 'Table_locks_immediate', 'Table_locks_waited', 'Created_tmp_disk_tables']
]

try {
    // Aleshin: addToClassPath not working for thread class loader
    try {
        driver = Class.forName('com.mysql.jdbc.Driver', true, Thread.currentThread().contextClassLoader).newInstance()

    } catch (Exception e) {
        File extPath = new File('ext')
        if (extPath.isDirectory() && extPath.exists()) {

            for (File mySQLConnectorPath : extPath.listFiles()) {
                if (mySQLConnectorPath.exists() && mySQLConnectorPath.name.toLowerCase().startsWith("mysql-connector-java")) {
                    URLClassLoader loader = new URLClassLoader(mySQLConnectorPath.toURI().toURL());
                    driver = Class.forName('com.mysql.jdbc.Driver', true, loader).newInstance();
                    break;
                }
            }
        }
    }
} catch (e) {
    warn("Could not find MySQL Connector in ext folder!")
}

gSql = null

def lock = new Object()

muteWarnings = false

validateConnection = {
    synchronized (lock) {
        if (!gSql?.connection?.isValid(60)) {
            try {
                gSql = new Sql(driver.connect('jdbc:mysql://' + serverHost + ':' + port.value, jdbc.credentials))
                muteWarnings = false
            } catch (java.sql.SQLException e) {
                if (!muteWarnings)
                    warn('MySQL monitor lost connectivity to ' + serverHost + '.')
                muteWarnings = true
                throw e
            }
        }
    }
}

connect = {
    /* Connect */
    validateConnection()

    /* Filter out non-available variables */
    valueMap = gSql.rows('SHOW STATUS').collectEntries { [it['Variable_name'], it['Value']] }

    statusVariables.each { group, counters ->
        statusVariables[group] = counters.findAll { valueMap.containsKey(it) }
    }

    /* Create counter groups */
    statusVariables.each { group, counters ->
        counterGroup(group) {
            counters.each { counter(it, type: Long) }
        }
    }
}

disconnect = {
    gSql.close()
}

statusVariables.each { group, counters ->
    counterGroup(group) {
        counters.each { counter(it, type: Long) }
    }
}

onUpdate = {
    validateConnection()

    valueMap = null
    try {
        valueMap = gSql.rows('SHOW STATUS').collectEntries { [it['Variable_name'], it['Value']] }
    } catch (java.sql.SQLException e) {
        validateConnection()
    }

    if (valueMap) {
        statusVariables.each { group, counters ->
            countersWithValues = [:] as Map
            counters.each {
                countersWithValues.put(it, valueMap[it].toLong())
            }
            updateCounter(group).with(countersWithValues)
        }
    }
}

settings(label: "General") {
    jdbc.settingsPageEntries.each { node(it) }
}

defaultStatistics << statistic('Query count', 'Slow_queries')
defaultStatistics << statistic('Query count', 'Queries')
