//
// Copyright 2014 SmartBear Software
//

/**
 * Tomcat Server Monitor using JMX.
 *
 * @id com.eviware.loadui.monitors.Tomcat
 * @category application servers
 * @help http://loadui.org/Server-monitoring/tomcat-server-monitor.html
 * @module jmx
 */

import com.eviware.loadui.pro.api.monitoring.MonitorRequestException

monitorName = createProperty('monitorName', String, null)

_jmxUriPattern.value = "service:jmx:rmi:///jndi/rmi://<host>:<port>/jmxrmi"

def counters = []

counters << jmx.group('Cache', 'Catalina', 'type=Cache', '<host><context>')
        .counter('cacheSize', 1024)
        .counter('hitsCount')
        .counter('accessCount')

counters << jmx.group('Session', 'Catalina', 'type=Manager', '<host><context>')
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

counters << jmx.group('Request Processor', 'Catalina', 'type=GlobalRequestProcessor', '<name>')
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

counters << jmx.group('Data Source', 'Catalina', 'type=DataSource', '<name> <host><path>')
        .counter('numActive')
        .counter('numIdle')
        .counter('maxActive')
        .counter('maxIdle')
        .calculated('% Num Active', {
    def numActive = it.value('numActive')
    def maxActive = it.value('maxActive')
    maxActive == 0 ? 0 : 100 * numActive / maxActive
}, Double)

counters << jmx.group('Thread Pool', 'Catalina', 'type=ThreadPool', '<name>')
        .counter('currentThreadsBusy')
        .counter('currentThreadCount')
        .counter('maxThreads')
        .calculated('% Current Threads Busy', {
    def currentThreadsBusy = it.value('currentThreadsBusy')
    def currentThreadCount = it.value('currentThreadCount')
    currentThreadCount == 0 ? 0 : 100 * currentThreadsBusy / currentThreadCount
}, Double)

counters << jmx.group('VM Garbage Collector', 'java.lang', 'type=GarbageCollector', '<name>')
        .counter('CollectionCount')
        .counter('CollectionTime')

counters << jmx.group('VM Threading', 'java.lang', 'type=Threading', 'Total')
        .counter('ThreadCount')
        .counter('PeakThreadCount')
        .counter('DaemonThreadCount')

counters << jmx.group('VM Memory', 'java.lang', 'type=Memory', 'Total')
        .counter('HeapMemoryUsage#committed', 'Heap Memory Commited', 1024)
        .counter('HeapMemoryUsage#max', 'Heap Memory Max', 1024)
        .counter('HeapMemoryUsage#used', 'Heap Memory Used', 1024)
        .calculated('% Heap Memory Used', {
    def used = it.value('HeapMemoryUsage#used')
    def max = it.value('HeapMemoryUsage#max')
    max == 0 ? 0 : 100 * used / max
}, Double)

/* 
 * Map that keeps source names mapped to their group name.
 * Map keys are group names and values are values transformed 
 * using transformGroupName method.
 */
def sources = [:]

/* 
 * Queries groups using domain name and type provided in input
 * argument which is of CounterGroupSupport type and transforms 
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
                        log.debug("Counter not found. Does this counter exist? Group: ${group.name} Counter: ${counter.name}, ObjectName: ${jmxObjectHolder.objectName}")
                    } else if (attribute.error) {
                        log.debug("Error in counter value. Group: ${group.name} Counter: ${counter.name} Error: [${attribute.toString()}], ObjectName: ${jmxObjectHolder.objectName}")
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
            warn('Tomcat monitor lost connectivity.')
        muteWarnings = true
        throw new MonitorRequestException("Could not connect to server", e)
    }
}

defaultStatistics << statistic('VM Memory', 'Heap Memory Used', 'Total')
defaultStatistics << statistic('Physical IO (per sec)', 'physical reads')
