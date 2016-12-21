/**
 * Attaches values from a CSV-file to incoming Virtual Users. These values can
 * then be used by subsequent components, such as a SoapUI Runner.
 *
 * @id com.eviware.DataSource
 * @category flow
 */

import au.com.bytecode.opencsv.CSVReader

createOutgoing('output') // Creates an outgoing connector called "output".
csvIterator = null

List dataList = []
List columns = []

createProperty('separatorSymbol', String, ',')

parseCsv = {
    if (inputFile.value) {
        File input = inputFile.value
        input.withReader { Reader reader ->
            def csvReader = new CSVReader(reader, separatorSymbol.value as char)
            dataList = csvReader.readAll()
            resetIterator()
        }
    }
}

resetIterator = {
    csvIterator = dataList.iterator()
    // first row should contain column names
    if (csvIterator.hasNext()) columns = csvIterator.next()
}

createProperty('inputFile', File) {
    parseCsv() // This will be called whenever the property's value is changed.
}

createProperty('shouldLoop', Boolean, false)

// This is called whenever we get an incoming message.
onMessage = { sendingConnector, receivingConnector, message ->

    if (!csvIterator?.hasNext() && shouldLoop.value) {
        resetIterator()
    }

    if (csvIterator?.hasNext()) {
        List line = csvIterator.next()
        columns.eachWithIndex { entry, int i ->
            message[entry] = i < line.size() ? line[i] : ''
        }
    }

    send(output, message)
}

onAction('RESET') {
    parseCsv()
}

layout {
    property(property: inputFile, constraints: 'width 220', label: 'Input file')
    property(property: shouldLoop, label: 'Loop')
}

settings(label: 'General') {
    property(property: separatorSymbol, label: 'Separator')
}

parseCsv()
