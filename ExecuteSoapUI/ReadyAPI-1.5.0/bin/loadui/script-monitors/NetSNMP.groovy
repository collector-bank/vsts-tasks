//
// Copyright 2014 SmartBear Software
//

/**
 * This monitor can be used to monitor most common Unix-like operating systems, including Linux, MacOS X and *BSD. It requires Net-SNMP to be installed and running on the monitored machine. The default settings is compatible with the default settings of Net-SNMP.
 *
 * @name UN*X
 * @id com.eviware.loadui.monitors.NetSNMP
 * @category operating systems
 * @help http://loadui.org/Server-monitoring/unix-server-monitor.html
 * @module snmp
 */

import com.eviware.loadui.pro.api.monitoring.MonitorRequestException

import java.util.concurrent.TimeUnit

monitorName = createProperty('monitorName', String, null)

BYTES_PER_MB = 1048576L
KBYTES_PER_MB = 1024L
UPDATE_INTERVAL = 5
DEFAULT_UPDATE_INTERVAL = '1.3.6.1.4.1.8072.1.5.1.0'
UPDATE_INTERVALS = '1.3.6.1.4.1.8072.1.5.3.1.2'

SWAP_CONFIGURED = '1.3.6.1.4.1.2021.4.3.0'
SWAP_FREE = '1.3.6.1.4.1.2021.4.4.0'
CACHED_MEMORY = '1.3.6.1.4.1.2021.4.15.0'

CPU_USER = '1.3.6.1.4.1.2021.11.50.0'
CPU_NICE = '1.3.6.1.4.1.2021.11.51.0'
CPU_SYSTEM = '1.3.6.1.4.1.2021.11.52.0'
CPU_IDLE = '1.3.6.1.4.1.2021.11.53.0'

HDD_DEVICELABEL_PATTERN = /.*[h|s]da\d{0,2}/

createProperty('monitorRAM', Boolean, true)
createProperty('monitorSwap', Boolean, true)
createProperty('monitorDisk_HostResources', Boolean, false)
createProperty('monitorMisc', Boolean, true)
createProperty('monitorUcdDiskIO', Boolean, true)

monitorDisks = true

ram = [name: 'RAM', oid: '1.3.6.1.2.1.25.2.1.2', enabled: monitorRAM.value]
swap = [name: 'Swap', oid: '1.3.6.1.2.1.25.2.1.3', enabled: monitorSwap.value]
disk = [name: 'Disk', oid: '1.3.6.1.2.1.25.2.1.4', enabled: monitorDisk_HostResources.value]
storages = [ram, disk, swap]

cpu = [name: 'CPU', updateInterval: null, enabled: true, executor: null]

ucdDskTable_enabled = true

previousCpuUsageMap = null

updateTasks = [] as Set

/*** UCD.dskTable Disk Monitoring ***/
dskTable_Total = '1.3.6.1.4.1.2021.9.1.6.'
dskTable_FirstDiskIndex = '1.3.6.1.4.1.2021.9.1.1.1'
dskTable_Device = '1.3.6.1.4.1.2021.9.1.3'
dskTable_Used = '1.3.6.1.4.1.2021.9.1.8.'

/*** UCD.diskIO Disk Monitoring ***/
diskIO_FirstDiskIndex = '1.3.6.1.4.1.2021.13.15.1.1.1.1'
diskIO_Device = '1.3.6.1.4.1.2021.13.15.1.1.2'
diskIO_NRead = '1.3.6.1.4.1.2021.13.15.1.1.3.'
diskIO_NWritten = '1.3.6.1.4.1.2021.13.15.1.1.4.'
diskIO_NReads = '1.3.6.1.4.1.2021.13.15.1.1.5.'
diskIO_NWrites = '1.3.6.1.4.1.2021.13.15.1.1.6.'

/*** Misc Monitoring ***/
NO_OF_PROCESSES = '1.3.6.1.2.1.25.1.6.0'

/*** Host Resources Disk Monitoring ***/
hrStorage_Size = '1.3.6.1.2.1.25.2.3.1.5.'
hrStorage_Used = '1.3.6.1.2.1.25.2.3.1.6.'
hrStorage_Types = '1.3.6.1.2.1.25.2.3.1.2.'
hrStorage_AllocationUnits = '1.3.6.1.2.1.25.2.3.1.4.'
hrStorage_Descr = '1.3.6.1.2.1.25.2.3.1.3.'

final def $lock = new Object[0]

connect = {
    synchronized ($lock) {
        log.debug("Connecting")
        updateTasks.clear()

        /*** General connection check ***/
        try {
            snmp.get("1.3.6.1.4.1.2021.4.1.0")
        } catch (e) {
            throw new MonitorRequestException("Could not connect to server", e)
        }

        /*** UCD.dskTable Disk Monitoring ***/
        dskTableDevices = [] as Set

        if (monitorDisks) {
            // Check if UCD-SNMP-MIB.dskTable is available. If not, enable the not as powerful HOST-RESOURCES-V2-MIB.hrStorageTable.
            UCD_dskTable_installed = false
            try {
                snmp.get(dskTable_FirstDiskIndex)
                UCD_dskTable_installed = true
            }
            catch (MonitorRequestException e) {
                log.debug("UCD.dskTable disabled")
                disk.enabled = true
            }

            if (UCD_dskTable_installed) {
                log.debug("UCD.dskTable enabled")
                snmp.getSubtree(dskTable_Device).findAll { it.value ==~ HDD_DEVICELABEL_PATTERN }.each { oid, label ->
                    i = snmp.lastNumberOfOid(oid)
                    dskTableDevices.add([label: label, index: i])

                    updateTasks << {
                        updateCounter('Disk', label).with(
                                'Total size': snmp.get(dskTable_Total + i),
                                'Used': snmp.get(dskTable_Used + i)
                        )
                    }
                }
                counterGroup('Disk', dskTableDevices*.label) {
                    counter('Total size', type: Double, description: 'No description.')
                    counter('Used', type: Double, description: 'No description.')
                }

                //			updateCounter( 'Disk', "/dev/sda1" ).with(
                //					'Total size':	15
                //				)
            }
        }

        /*** UCD.diskIO Disk Monitoring ***/
        dskIODevices = [] as Set

        if (monitorDisks) {
            // check for UCD-DISKIO-MIB
            try {
                snmp.get(diskIO_FirstDiskIndex)
            }
            catch (MonitorRequestException e) {
                log.debug("dskIODevices disabled")
                monitorUcdDiskIO.value = false
            }

            if (monitorUcdDiskIO.value) {
                log.debug("dskIODevices enabled")
                snmp.getSubtree(diskIO_Device).findAll { it.value ==~ HDD_DEVICELABEL_PATTERN }.each { oid, label ->
                    i = snmp.lastNumberOfOid(oid)
                    dskIODevices << [label: label, index: snmp.lastNumberOfOid(oid)]

                    updateTasks << {
                        updateCounter('Ucd Disk IO', label).with(
                                'Bytes read': snmp.nonExceptionalGet(diskIO_NRead + i),
                                'Bytes written': snmp.nonExceptionalGet(diskIO_NWritten + i),
                                'Read accesses': snmp.nonExceptionalGet(diskIO_NReads + i),
                                'Write accesses': snmp.nonExceptionalGet(diskIO_NWrites + i),
                        )
                    }
                }

                counterGroup('Ucd Disk IO', dskIODevices*.label) {
                    counter('Bytes read', type: Long, description: 'No description.')
                    counter('Bytes written', type: Long, description: 'No description.')
                    counter('Read accesses', type: Long, description: 'No description.')
                    counter('Write accesses', type: Long, description: 'No description.')
                }
            }
        }

        /*** Misc Monitoring ***/
        if (monitorMisc.value) {
            counterGroup('Misc') { counter('Number of processes', type: Long, description: 'No description.') }
            updateTasks << { updateCounter('Misc').with('Number of processes': snmp.get(NO_OF_PROCESSES)) }
        }

        /*** Host Resources Disk Monitoring ***/
        devices = null

        if (storages.any { it.enabled }) {
            devices = snmp.getSubtree(hrStorage_Types)

            storages.findAll { it.enabled }.each { storageType ->

                subStorageIndices = devices.findAll { it.value == storageType.oid }
                subStorages = [] as Set
                subStorageIndices.each { k, v ->
                    i = snmp.lastNumberOfOid(k)
                    subStorages << ['index': i, 'description': snmp.get(hrStorage_Descr + i), 'multiplier': snmp.get(hrStorage_AllocationUnits + i)]

                }

                counterGroup(storageType.name, subStorages*.description) {
                    counter("Total size", type: Integer, description: "No description.")
                    counter("Used", type: Integer, description: "No description.")
                }

                storageType['subStorages'] = subStorages

                updateTasks << {
                    storageType.subStorages.each { device ->
                        updateCounter(storageType.name, device.description).with(
                                'Total size': snmp.get(hrStorage_Size + device.index) * device.multiplier / BYTES_PER_MB,
                                'Used': snmp.get(hrStorage_Used + device.index) * device.multiplier / BYTES_PER_MB
                        )
                    }
                }
            }
        }

        /*** UCD.systemStats CPU Monitoring ***/
        if (cpu.enabled) {
            // Look for subtree specific update interval
            updateIntervals = snmp.getSubtree(UPDATE_INTERVALS)
            updateIntervals.each { oid, updateInterval ->
                subTree = oid - "$UPDATE_INTERVALS."
                if (CPU_IDLE.find(subTree))
                    cpu.updateInterval = updateInterval
            }
            // Resort to default update interval
            if (!cpu.updateInterval)
                cpu.updateInterval = snmp.get(DEFAULT_UPDATE_INTERVAL)

            if (cpu.updateInterval <= 1)
                cpu.updateInterval = UPDATE_INTERVAL

            cint = cpu.updateInterval

            counterGroup(cpu.name) {
                counter('Usage (%)', type: Double, description: 'No description.')
            }
        }

        log.debug("Connecting done")
    }
}


disconnect = {
    log.debug("Disconnecting")
}

muteWarnings = false

duringPhase('START') {
    muteWarnings = false
    if (cpu.enabled) {
        cpu.executor = scheduleAtFixedRate({
            def cpuUsageMap = snmp.get(CPU_USER, CPU_NICE, CPU_SYSTEM, CPU_IDLE)
            if (previousCpuUsageMap != null) {
                def deltaMap = cpuUsageMap.collectEntries { k, v ->
                    [k, v - previousCpuUsageMap.getAt(k)]
                }
                def sum = 0
                deltaMap.each { entry ->
                    sum += entry.value
                }
                def usage = 100 * (1 - deltaMap.getAt(CPU_IDLE) / sum)
                updateCounter(cpu.name).with(
                        'Usage (%)': usage
                )
            }
            previousCpuUsageMap = cpuUsageMap
        }, 0, cpu.updateInterval, TimeUnit.SECONDS)
    }
}

duringPhase('STOP') {
    if (cpu.enabled)
        cpu.executor?.cancel(true)
}

onUpdate = {
    synchronized ($lock) {
        updateTasks.each {
            try {
                it()
            }
            catch (e) {
                log.debug("Exception in closure while updating counters: ${e.getMessage()}" )
                if (!muteWarnings) {
                    muteWarnings = true
                    warn('UN*X monitor failed to connect.')
                }
            }
        }
    }
}

settings(label: "General") {
    snmp.settingsPageEntries.each { node(it) }
}

defaultStatistics << statistic('CPU', 'Usage (%)')
defaultStatistics << statistic('Misc', 'Number of processes')
