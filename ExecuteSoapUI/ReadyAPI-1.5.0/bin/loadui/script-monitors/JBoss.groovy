//
// Copyright 2014 SmartBear Software
//

/**
 * JBoss Server Monitor using JMX.
 *
 * @id com.eviware.loadui.monitors.JBoss
 * @category application servers
 * @help http://loadui.org/Server-monitoring/jboss-server-monitor.html
 * @module jmx
 */

import com.eviware.loadui.pro.api.monitoring.MonitorRequestException

monitorName = createProperty('monitorName', String, null)

_jmxUriPattern.value = "service:jmx:rmi:///jndi/rmi://<host>:<port>/jmxrmi"

def counters = []

counters << jmx.group('Cache', 'jboss.web', 'type=Cache', '<host><path>')
        .counter('cacheSize', 1024)
        .counter('hitsCount')
        .counter('accessCount')

counters << jmx.group('Session', 'jboss.web', 'type=Manager', '<host><path>')
        .counter('activeSessions')
        .counter('sessionCounter')
        .counter('maxActive')
        .counter('sessionMaxAliveTime', 1000)
        .counter('sessionAverageAliveTime', 1000)
        .counter('rejectedSessions')
        .counter('expiredSessions')
        .calculated('% Rejected Sessions', {
    def rejectedSessions = it.value('rejectedSessions')
    def maxActive = it.value('maxActive')
    maxActive == 0 ? 0 : 100 * rejectedSessions / maxActive
}, Double)

counters << jmx.group('Request Processor', 'jboss.web', 'type=GlobalRequestProcessor', '<name>')
        .counter('requestCount')
        .counter('errorCount')
        .counter('bytesSent', 1024)
        .counter('maxTime', 1000)
        .counter('processingTime', 1000)
        .calculated('% Error Count', {
    def errorCount = it.value('errorCount')
    def requestCount = it.value('requestCount')
    requestCount == 0 ? 0 : 100 * errorCount / requestCount
}, Double)

counters << jmx.group('Data Source', 'jboss.jca', 'service=ManagedConnectionPool', '<name>')
        .counter('InUseConnectionCount')
        .counter('MaxConnectionsInUseCount')
        .counter('ConnectionCount')
        .counter('MaxSize')
        .counter('ConnectionCreatedCount')
        .counter('ConnectionDestroyedCount')
        .calculated('% In Use Connection Count', {
    def inUseConnectionCount = it.value('InUseConnectionCount')
    def maxConnectionsInUseCount = it.value('MaxConnectionsInUseCount')
    maxConnectionsInUseCount == 0 ? 0 : 100 * inUseConnectionCount / maxConnectionsInUseCount
}, Double)

counters << jmx.group('Thread Pool', 'jboss.web', 'type=ThreadPool', '<name>')
        .counter('currentThreadsBusy')
        .counter('currentThreadCount')
        .counter('maxThreads')
        .calculated('% Current Threads Busy', {
    def currentThreadsBusy = it.value('currentThreadsBusy')
    def currentThreadCount = it.value('currentThreadCount')
    currentThreadCount == 0 ? 0 : 100 * currentThreadsBusy / currentThreadCount
}, Double)

counters << jmx.group('Transactions', 'jboss.jta', 'name=TransactionStatistics', 'Total')
        .counter('NumberOfCommittedTransactions', 'Commited Count')
        .counter('NumberOfResourceRollbacks', 'Resource Rollbacks')
        .counter('NumberOfAbortedTransactions', 'Aborted Count')
        .counter('NumberOfNestedTransactions', 'Nested Count')
        .counter('NumberOfApplicationRollbacks', 'Application Rollbacks')
        .counter('NumberOfInflightTransactions', 'Inflight Count')
        .counter('NumberOfTransactions', 'Transaction Count')
        .counter('NumberOfHeuristics', 'Heuristics Count')
        .counter('NumberOfTimedOutTransactions', 'Timed Out Count')

counters << jmx.group('JMS Queue', 'org.hornetq', 'module=JMS,type=Queue', '<name>')
        .counter('ConsumerCount') //in process (currently processed) messages
        .counter('ScheduledCount') //number of scheduled for processing messages
        .counter('MessageCount') //depth (probably)
        .counter('DeliveringCount') //receivers count
        .counter('MessagesAdded')

counters << jmx.group('JMS Topic', 'org.hornetq', 'module=JMS,type=Topic', '<name>')
        .counter('DurableMessageCount')
        .counter('NonDurableMessageCount')
        .counter('DurableSubscriptionCount')
        .counter('NonDurableSubscriptionCount')
//.calculated('Total Message Count', {
//	it.value('DurableMessageCount') +  it.value('NonDurableMessageCount')
//})
//.calculated('Total Subscription Count', {
//	it.value('DurableSubscriptionCount') +  it.value('NonDurableSubscriptionCount')
//})

counters << jmx.group('EJB3', 'jboss.j2ee', 'service=EJB3,name=*', '<name>')
//.counter('CacheSize')
//.counter('PassivatedCount')
        .counter('CreateCount')
        .counter('CurrentSize')
        .counter('RemoveCount')
        .counter('MaxSize')
        .counter('AvailableCount')
//.counter('TotalSize')

counters << jmx.group('EJB', 'jboss.j2ee', 'service=EJB', '<jndiName>', 'plugin=*,service=EJB')
//.counter('CacheSize')
        .counter('CreateCount')
        .counter('MaxPoolSize')
        .counter('CurrentPoolSize')
        .counter('RemoveCount')

counters << jmx.group('Server Info', 'jboss.system', 'type=ServerInfo', 'Total')
        .counter('ActiveThreadCount')
        .counter('MaxMemory')
        .counter('TotalMemory')
        .counter('FreeMemory')

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

disconnect = {}
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
            warn('JBoss monitor lost connectivity.')
        muteWarnings = true
        throw new MonitorRequestException("Could not connect to server", e)
    }
}

defaultStatistics << statistic('Request Processor', 'Processing Time')
