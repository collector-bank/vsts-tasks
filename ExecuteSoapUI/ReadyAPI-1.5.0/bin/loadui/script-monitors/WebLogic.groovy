//
// Copyright 2014 SmartBear Software
//

/**
 * WebLogic Server Monitor using JMX.
 *
 * @id com.eviware.loadui.monitors.WebLogic
 * @category application servers
 * @help http://loadui.org/Server-monitoring/weblogic-server-monitor.html
 * @module jmx
 */

import com.eviware.loadui.pro.api.monitoring.MonitorRequestException
import org.codehaus.groovy.runtime.StackTraceUtils

monitorName = createProperty('monitorName', String, null)

_jmxUriPattern.value = "service:jmx:rmi://<host>:<port>/jndi/iiop://<host>:<port>/weblogic.management.mbeanservers.runtime"

def counters = []

counters << jmx.group('Server', 'com.bea', 'Type=ServerRuntime', '<Name>')
        .counter('SocketsOpenedTotalCount')
        .counter('OpenSocketsCurrentCount')

counters << jmx.group('Session', 'com.bea', 'Type=WebAppComponentRuntime', '<Name>')
        .counter('OpenSessionsCurrentCount')
        .counter('OpenSessionsHighCount')
        .counter('SessionsOpenedTotalCount')

counters << jmx.group('JVM', 'com.bea', 'Type=JVMRuntime', '<ServerRuntime>')
        .counter('HeapFreeCurrent')
        .counter('HeapSizeCurrent')

counters << jmx.group('Connector', 'com.bea', 'Type=ConnectorServiceRuntime', '<ServerRuntime>')
        .counter('ConnectionPoolsTotalCount')
        .counter('ConnectionPoolCurrentCount')

counters << jmx.group('JMS Total', 'com.bea', 'Type=JMSRuntime', '<ServerRuntime>')
        .counter('ConnectionsCurrentCount')
        .counter('ConnectionsHighCount')
        .counter('ConnectionsTotalCount')
        .counter('JMSServersCurrentCount')
        .counter('JMSServersTotalCount')
        .counter('JMSServersHighCount')

counters << jmx.group('JMS Servers', 'com.bea', 'Type=JMSServerRuntime', '<ServerRuntime>/<Name>')
        .counter('DestinationsCurrentCount')
        .counter('DestinationsHighCount')
        .counter('DestinationsTotalCount')
        .counter('MessagesCurrentCount')
        .counter('MessagesHighCount')
        .counter('MessagesPendingCount')
        .counter('MessagesReceivedCount')
        .counter('SessionPoolsCurrentCount')
        .counter('SessionPoolsHighCount')
        .counter('SessionPoolsTotalCount')

counters << jmx.group('JTA', 'com.bea', 'Type=JTARuntime', '<ServerRuntime>')
        .counter('TransactionTotalCount')
        .counter('TransactionCommittedTotalCount')
        .counter('TransactionRolledBackTotalCount')
        .counter('TransactionRolledBackTimeoutTotalCount')
        .counter('TransactionRolledBackResourceTotalCount')
        .counter('TransactionRolledBackAppTotalCount')
        .counter('TransactionRolledBackSystemTotalCount')
        .counter('TransactionHeuristicsTotalCount')
        .counter('TransactionAbandonedTotalCount')
        .counter('ActiveTransactionsTotalCount')
        .counter('SecondsActiveTotalCount')

counters << jmx.group('Queue', 'com.bea', 'Type=ExecuteQueueRuntime', '<ServerRuntime>/<Name>')
        .counter('PendingRequestCurrentCount')
        .counter('ExecuteThreadTotalCount')
        .counter('ExecuteThreadCurrentIdleCount')
        .counter('ServicedRequestTotalCount')

counters << jmx.group('EJB Pool', 'com.bea', 'Type=EJBPoolRuntime', '<ServerRuntime>/<Name>')
        .counter('AccessTotalCount')
        .counter('BeansInUseCurrentCount')
        .counter('DestroyedTotalCount')
        .counter('MissTotalCount')
        .counter('PooledBeansCurrentCount')
        .counter('TimeoutTotalCount')
        .counter('WaiterCurrentCount')

counters << jmx.group('EJB Transaction', 'com.bea', 'Type=EJBTransactionRuntime', '<ServerRuntime>/<Name>')
        .counter('TransactionsCommittedTotalCount')
        .counter('TransactionsRolledBackTotalCount')
        .counter('TransactionsTimedOutTotalCount')

counters << jmx.group('EJB Cache', 'com.bea', 'Type=EJBCacheRuntime', '<ServerRuntime>/<Name>')
        .counter('ActivationCount')
        .counter('CacheAccessCount')
        .counter('CachedBeansCurrentCount')
        .counter('CacheMissCount')
        .counter('PassivationCount')

counters << jmx.group('Thread', 'com.bea', 'Type=ThreadPoolRuntime', '<ServerRuntime>')
        .counter('CompletedRequestCount')
        .counter('ExecuteThreadIdleCount')
        .counter('ExecuteThreadTotalCount')
        .counter('HoggingThreadCount')
        .counter('MinThreadsConstraintsCompleted')
        .counter('MinThreadsConstraintsPending')
        .counter('PendingUserRequestCount')
        .counter('QueueLength')
        .counter('StandbyThreadCount')
        .counter('Throughput')

counters << jmx.group('Channel', 'com.bea', 'Type=ServerChannelRuntime', '<ServerRuntime>/<Name>')
        .counter('AcceptCount')
        .counter('ConnectionsCount')
        .counter('MessagesReceivedCount')
        .counter('MessagesSentCount')

counters << jmx.group('JDBC Connection Pool', 'com.bea', 'Type=JDBCDataSourceRuntime', '<ServerRuntime>/<Name>')
        .counter('ActiveConnectionsCurrentCount')
        .counter('WaitingForConnectionCurrentCount')
        .counter('NumUnavailable')
        .counter('ActiveConnectionsAverageCount')
        .counter('ConnectionDelayTime')
        .counter('ActiveConnectionsHighCount')
        .counter('ConnectionsTotalCount')
        .counter('CurrCapacity')
        .counter('FailuresToReconnectCount')
        .counter('HighestNumUnavailable')
        .counter('WaitSecondsHighCount')
        .counter('WaitingForConnectionHighCount')

counters << jmx.group('JRockit', 'com.bea', 'Type=JRockitRuntime', '<ServerRuntime>')
        .counter('HeapFreeCurrent')
        .counter('HeapSizeCurrent')
        .counter('FreeHeap')
        .counter('UsedHeap')
        .counter('TotalHeap')
        .counter('FreePhysicalMemory')
        .counter('UsedPhysicalMemory')
        .counter('TotalPhysicalMemory')
        .counter('NumberOfDaemonThreads')
        .counter('NumberOfProcessors')
        .counter('TotalGarbageCollectionCount')
        .counter('TotalGarbageCollectionTime')
        .counter('TotalNumberOfThreads')
        .counter('TotalNurserySize')
        .counter('AllProcessorsAverageLoad')
        .counter('JvmProcessorLoad')

/* 
 * Map that keeps source names mapped to their group name.
 * Map keys are group names and values are values transformed 
 * using transformGroupName method.
 */
def sources = [:]

/* 
 * Queries groups using domain name and type provided in input
 * argument which is of JmxCounterGroup type and transforms 
 * retrieved values into more appropriate human readable strings
 * using pattern from input argument. These transformed values 
 * are later used as source names.
 * 
 * Each group is actually a source. For example, group: 
 * Catalina:type=Cache,host=localhost,path=/examples 
 * retrieves session info for /examples application on 
 * localhost. And group:
 * Catalina:type=Cache,host=localhost,path=/ retrieves
 * session info for root application.
 * 
 * So these two groups are actually two sources of data,
 * but since these strings are not appropriate for GUI
 * they are transformed into:
 * 
 * localhost/examples and localhost/
 * 
 * using pattern <host><port>
 * 
 * All sources values are kept in a 'sources' map.
 * 
 * Returns the list of all transformed names.
 * 
 */
def transformGroupName = { group ->
    def sourcePattern = group.pattern
    def sourceMatcher = sourcePattern =~ /<(\w*)>/

    def objectList = jmx.listObjectNames(group.query, group.excludeQuery).collect { objectName ->
        def result = sourcePattern
        sourceMatcher.collect {
            def patternItem = it[1]
            def im = objectName =~ /$patternItem=(.*?)(,{1}|$)/
            im.collect {
                result = result.replaceAll(/<$patternItem>/, "${it[1]}")
            }
        }
        result = result.replaceAll(/"/, "")
        sources[(objectName)] = result
    }
}

connect = {
    sources.clear()

    // check connection
    try {
        jmx.listDomains()
    }
    catch (e) {
        throw new MonitorRequestException("Could not connect to server", e)
    }

    // build groups
    for (group in counters) {
        def objectList = transformGroupName(group)

        counterGroup(group.name, objectList) {
            for (c in group.counters) {
                counter(c.label, type: c.type)
            }
        }
    }
}

testConnection = {
    connect()
    def group = counters[0]
    def nativeCounters = group.counters.findAll { !it.calculated }.collect { it.name }
    try {
        jmx.query(group.query, nativeCounters, group.excludeQuery)
    } catch (e) {
        if (StackTraceUtils.extractRootCause(e) instanceof ClassNotFoundException)
            warn("Could not find wlclient.jar in ext folder!")
        throw new ClassNotFoundException("Could not find wlclient.jar in ext folder!")
    }
}
onStart = {}
onStop = {}

muteWarnings = false
onUpdate = {
    try {
        for (group in counters) {
            def nativeCounters = group.counters.findAll { !it.calculated }.collect { it.name }
            def calculatedCounters = group.counters.findAll { it.calculated }.collect { it.name }
            def jmxQueryResult = jmx.query(group.query, nativeCounters, group.excludeQuery)
            for (jmxObjectHolder in jmxQueryResult) {
                def map = [:]
                nativeCounters.each { counterName ->
                    def counter = group.counters.find { it.name == counterName }
                    def attribute = jmxObjectHolder.attributes.find { it.name == counterName }
                    if (attribute == null) {
                        log.debug("Counter not found. Does this counter exist? Group: ${group.name}, Counter: ${counter.name}, ObjectName: ${jmxObjectHolder.objectName}")
                    } else if (attribute.error) {
                        log.debug("Error in counter value. Group: ${group.name}, Counter: ${counter.name}, Error: [${attribute.toString()}], ObjectName: ${jmxObjectHolder.objectName}")
                    } else {
                        counter.value = attribute.value
                        map[(counter.label)] = counter.value / counter.scaleFactor
                    }
                }
                calculatedCounters.each { counterName ->
                    def counter = group.counters.find { it.name == counterName }
                    map[(counter.label)] = counter.value
                }
                updateCounter(group.name, sources[jmxObjectHolder.objectName]).with(map)
            }
        }
        muteWarnings = false
    } catch (e) {
        if (!muteWarnings)
            warn('WebLogic monitor lost connectivity.')
        muteWarnings = true
        throw new MonitorRequestException("Could not connect to server", e)
    }
}

defaultStatistics << statistic('JVM', 'Heap Free Current')
