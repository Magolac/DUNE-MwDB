
class fr.spirals.snt.dune.mwdb.Execution {
    att idExecution : String
    rel context : fr.spirals.snt.dune.mwdb.Context// with maxBound 1
    rel userInput : fr.spirals.snt.dune.mwdb.UserInput// with maxBound 1
    rel timeRange : fr.spirals.snt.dune.mwdb.TimeRange //with maxBound 1
    rel timeFrame : fr.spirals.snt.dune.mwdb.Metrics //with maxBound 1


}

index executions : fr.spirals.snt.dune.mwdb.Execution {
    idExecution
}

class fr.spirals.snt.dune.mwdb.Context {
    att device : String
    att sdk_version : String
    att api_level : String
    att screen_res : String
    att cpu_api : String
    att net_type : String
}

class fr.spirals.snt.dune.mwdb.Metrics {
    //att timeFrame : Double {
    //    using "PolynomialNode"
    //    with precision "0.1"
    //}
    att timeFrame : Double
}

class fr.spirals.snt.dune.mwdb.TimeRange {
    att startTime : Double
    att endTime : Double
}

class fr.spirals.snt.dune.mwdb.UserInput {
    att eventType : String
    att viewId : String
}
