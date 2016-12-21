//
// Copyright 2014 SmartBear Software
//

/**
 * Apache Server Monitor using mod_status.
 *
 * @id com.eviware.loadui.monitors.Apache
 * @category web servers
 */

import com.eviware.loadui.pro.api.monitoring.MonitorRequestException

import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import java.security.KeyStore

monitorName = createProperty('monitorName', String, null)
createProperty('apachePort', Long, 80)
createProperty('apacheIsSecure', Boolean, false)
useSSLClientAuthentication = createProperty('useSSLClientAuthentication', Boolean, false)
keystoreLocation = createProperty('keystoreLocation', String, 'jre/lib/security/cacerts')
keystorePassword = createProperty('keystorePassword', String, 'changeit')
clientKeyPassword = createProperty('clientKeyPassword', String, '')

def GROUP_BASIC = 'Basic'
def GROUP_EXTENDED = 'Extended'

def CNT_BASIC_BUSY = /(?i).*busy.*worker.*/
def CNT_BASIC_IDLE = /(?i).*idle.*worker.*/

def CNT_CALC_BUSY_REL = '% Busy Workers'

def extendedStatus = false

def calculateCounters = { map ->
    def busy = map.find { it.key.matches(CNT_BASIC_BUSY) }?.value as Double
    def idle = map.find { it.key.matches(CNT_BASIC_IDLE) }?.value as Double
    if (busy && idle && (busy + idle) != 0) {
        map[CNT_CALC_BUSY_REL] = 100 * busy / (busy + idle)
    } else {
        map[CNT_CALC_BUSY_REL] = 0
    }

    map
}

def statusContent = { String host, Long port, Boolean secure ->
    try {
        def urlString = "http"
        if (secure) {
            urlString += 's'
        }
        urlString = "${urlString}://${host}"
        if (port != -1) {
            urlString = "${urlString}:${port}"
        }
        urlString += "/server-status?auto"

        def url = new URL(urlString)
        def connection = openConnection(url)
        if (connection.responseCode == 200) {
            def map = [:]
            connection.content.eachLine {
                def item = it =~ /(.*)\s*:\s*([0-9]*\.?[0-9]+)/
                item.each {
                    it[1] = it[1].replaceAll(/([^ ]\p{Ll})(\p{Lu})/, "\$1 \$2");
                    it[1] = it[1].replaceAll(/(\p{Lu})(\p{Lu}\p{Ll})/, "\$1 \$2");
                    map[(it[1])] = it[2].matches(/.*\.{1}.*/) ? (it[2] as Double) : (it[2] as Long)
                }
            }
            muteWarnings = false
            return calculateCounters(map)

        } else {
            warnOnce()
            log.warn "Could not connect to server [responseCode: ${connection.responseCode}, responseMessage: ${connection.responseMessage}]"
            throw new MonitorRequestException("Could not connect to server [responseCode: ${connection.responseCode}, responseMessage: ${connection.responseMessage}]")
        }
    }
    catch (e) {
        warnOnce()
        e.printStackTrace()
        throw new MonitorRequestException("Could not connect to server", e)
    }
}

muteWarnings = false
warnOnce = {
    if (!muteWarnings)
        warn('Apache monitor lost connectivity.')
    muteWarnings = true
}

connect = {
    //will throw MonitorRequestException if status can't be fetched from the server
    def map = statusContent(server.host, apachePort.value, apacheIsSecure.value)

    def basic = map.findAll {
        it.key.matches(CNT_BASIC_BUSY) || it.key.matches(CNT_BASIC_IDLE)
    }
    if (!basic.isEmpty()) {
        counterGroup(GROUP_BASIC) {
            basic.each({
                counter(it.key, type: it.value.getClass())
            })
        }
    }

    def extended = map.minus(basic)
    if (!extended.isEmpty()) {
        counterGroup(GROUP_EXTENDED) {
            extended.each({
                counter(it.key, type: it.value.getClass())
            })
        }
    }
    extendedStatus = !extended.isEmpty()
}

openConnection = { URL url ->
    def connection = url.openConnection()

    if (useSSLClientAuthentication.value) {
        assert keystoreLocation.value
        assert keystorePassword.value
        KeyStore ks = KeyStore.getInstance("JKS")
        FileInputStream fis = new FileInputStream(keystoreLocation.value)
        ks.load(fis, keystorePassword.value.toCharArray())
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(ks, clientKeyPassword.value.toCharArray())

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(kmf.getKeyManagers(), null, null);

        connection.setSSLSocketFactory(sc.getSocketFactory())
    }
    connection
}

disconnect = {}
onStart = {}
onStop = {}

onUpdate = {
    def map = statusContent(server.host, apachePort.value, apacheIsSecure.value)
    def basic = map.findAll {
        it.key.matches(CNT_BASIC_BUSY) || it.key.matches(CNT_BASIC_IDLE)
    }
    updateCounter(GROUP_BASIC).with(basic)
    if (extendedStatus) {
        updateCounter(GROUP_EXTENDED).with(map.minus(basic))
    }
}

def testConnection = {
    try {
        connect()
        true
    }
    catch (e) {
        log.warn e.toString()
        false
    }
}

settings(label: "Connection") {
    property(property: apachePort, label: 'Port')
    property(property: apacheIsSecure, label: 'Is https?')
    property(property: useSSLClientAuthentication, label: 'Use client SSL Auth')
    property(property: keystoreLocation, label: 'Keystore location')
    property(property: keystorePassword, label: 'Keystore password')
    property(property: clientKeyPassword, label: 'Client auth password')
    def connectionStatus = "Untested..."
    action(label: "Test Connection", async: false, action: {
        connectionStatus = testConnection()
    }, status: { connectionStatus })
}

defaultStatistics << statistic('Extended', 'CPU Load')
defaultStatistics << statistic('Extended', 'Req Per Sec')
defaultStatistics << statistic('Basic', 'Busy Workers')
