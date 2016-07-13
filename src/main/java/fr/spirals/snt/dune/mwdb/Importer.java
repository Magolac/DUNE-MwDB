package fr.spirals.snt.dune.mwdb;

import org.mwg.Type;
import org.mwg.importer.ImporterActions;
import org.mwg.task.Task;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mwg.task.Actions.*;


/**
 * Created by ludovicmouline on 14/06/16.
 */
public class Importer {

    public static final String CONTEXT_FILE = "context.csv";
    public static final String METRICS_FILE = "metrics.csv";
    public static final String TIMERANGE_FILE = "timerange.csv";
    public static final String USERINPUT_FILE = "userinput.csv";

    public static final String CSV_SEP = ",";

    private static final Task contextImporter = newTask();
    private static final Task metricsImporter = newTask();
    private static final Task timerangeImporter = newTask();
    private static final Task userinputImporter = newTask();

    private static final Task importTask = newTask();

    private static Importer singleton;

    private DuneModel duneModel;

    private Importer(DuneModel model){
        this.duneModel = model;

        contextImporter
                .setTime("0")
                .action(ImporterActions.READLINES,"{{path}}")
                .foreach(
                        selectObject(line -> !((String)line).startsWith("device")  && !((String)line).isEmpty())
                                .foreach(
                                        split(",")
                                                .asVar("res")
                                                .newTypedNode(Context.NODE_NAME)
                                                .setProperty(Context.DEVICE,Type.STRING,"{{res[0]}}")
                                                .setProperty(Context.SDK_VERSION,Type.STRING,"{{res[1]}}")
                                                .setProperty(Context.API_LEVEL,Type.STRING,"{{res[2]}}")
                                                .setProperty(Context.SCREEN_RES,Type.STRING,"{{res[3]}}")
                                                .setProperty(Context.CPU_API,Type.STRING,"{{res[4]}}")
                                                .setProperty(Context.NET_TYPE,Type.STRING,"{{res[5]}}")
                                                .asVar("taskContext")
                                                .fromVar("executionNode")
                                                .add(Execution.CONTEXT,"taskContext")
                                                .setVar("taskContext",null)
                                )
                );

        metricsImporter
                .setTime("{{startTime}}")
                .setVar("isFirst",true)
                .setVar("currentTime","{{startTime}}")
                .action(ImporterActions.READLINES,"{{path}}")
                .selectObject(line -> !((String)line).startsWith("timeFrame") && !((String)line).isEmpty())
                .foreach(
                        asVar("res")
                        .ifThen(context -> !((boolean)context.variable("isFirst")),
                                print("else")
                                .fromVar("executionNode")
                                        .jump("{{currentTime}}")
                                        //todo fix
                                        .setTime("{{currentTime}}")
                                        .traverse(Execution.TIMEFRAME)
                                        .asVar("metrics")
                                        .fromVar("metrics")
                                        .jump("{{currentTime}}")
                                        .setProperty(Metrics.TIMEFRAME,Type.DOUBLE,"{{res}}")
                                )
                        .ifThen(context -> (boolean)context.variable("isFirst"),
                                setVar("isFirst",false)
                                        .setTime("{{currentTime}}")
                                        .newTypedNode(Metrics.NODE_NAME)
                                        .setProperty(Metrics.TIMEFRAME,Type.DOUBLE,"{{res}}")
                                        .asVar("metric")
                                        .fromVar("executionNode")
                                        .jump("{{currentTime}}")
                                        .add(Execution.TIMEFRAME,"metric")
                        )
                        .setVar("currentTime","{{=currentTime + res}}")

                )
                .fromVar("executionNode")
                .jump("{{currentTime}}")
                //todo fix
                .setTime("{{currentTime}}")
                .traverse(Execution.TIMEFRAME)
                .asVar("metrics")
                .fromVar("metrics")
                .jump("{{currentTime}}")
                .setProperty(Metrics.TIMEFRAME,Type.DOUBLE,"-1.")
                .setVar("currentTime",null)
                .setVar("isFirst",null);

        timerangeImporter
                .action(ImporterActions.READLINES,"{{path}}")
                .selectObject(oLine -> !((String)oLine).startsWith("st") && !((String)oLine).isEmpty())
                            .foreach(
                                    split(",")
                                            .asVar("res")
                                            .newTypedNode(TimeRange.NODE_NAME)
                                            .setProperty(TimeRange.STARTTIME, Type.DOUBLE,"{{res[0]}}")
                                            .setProperty(TimeRange.ENDTIME, Type.DOUBLE,"{{res[1]}}")
                                            .asVar("timeRange")
                                            .fromVar("executionNode")
                                            .add(Execution.TIMERANGE,"timeRange")
                                            .setVar("startTime","{{res[0]}}")
                                            .setVar("timeRange",null)
                                            .setVar("res",null)
                            );


        userinputImporter
                .action(ImporterActions.READLINES,"{{path}}")
                .selectObject(oLine -> !((String)oLine).startsWith("timestamp") && !((String)oLine).isEmpty())
                .foreach(
                        split(",")
                                .asVar("res")
                                .fromVar("executionNode")
                                .jump("{{res[0]}}")
                                //todo fix
                                .setTime("{{res[0]}}")
                                .traverse(Execution.USERINPUT)
                                .asVar("userInputs")
                                .ifThen(context -> ((Object[])context.variable("userInputs")).length  == 0,
                                        setTime("{{res[0]}}")
                                                .newTypedNode(UserInput.NODE_NAME)
                                                .asVar("userInput"))
                                .ifThen(context -> ((Object[])context.variable("userInputs")).length  == 1,
                                        fromVar("{{userInputs[0]}}")
                                                .jump("{{res[0]}}")
                                                .asVar("userInput"))
                                .fromVar("userInput")
                                .setProperty(UserInput.EVENTTYPE,Type.STRING,"{{res[1]}}")
                                .setProperty(UserInput.VIEWID,Type.STRING,"{{res[2]}}")
                                .fromVar("executionNode")
                                .jump("{{res[0]}}")
                                .add(Execution.USERINPUT,"userInput")
                                .setVar("res",null)
                                .setVar("userInput",null)
                );




        importTask
                .setWorld("0")
                .setTime("0")
                .setVar("executionId","execution1")
                .newTypedNode(Execution.NODE_NAME)
                .setProperty(Execution.IDEXECUTION,Type.STRING,"executionId")
                .indexNode(DuneModel.IDX_EXECUTIONS,Execution.IDEXECUTION)
                .asVar("executionNode")
                .action(ImporterActions.READFILES, "{{folder}}")
                .asVar("filesPath")
                .selectObject(oFile -> ((String)oFile).endsWith(TIMERANGE_FILE))
                .then(context -> context.setResult(context.resultAsObjectArray()[0]))
                .asVar("path")
                .executeSubTask(timerangeImporter)
                .fromVar("filesPath")
                .selectObject(oFile -> !((String)oFile).endsWith(TIMERANGE_FILE))
                .foreach(
                         asVar("path")
                         .ifThen(context -> ((String)context.variable("path")).endsWith(CONTEXT_FILE), contextImporter)
                         .ifThen(context -> ((String)context.variable("path")).endsWith(METRICS_FILE),
                                 metricsImporter)
                         .ifThen(context -> ((String)context.variable("path")).endsWith(TIMERANGE_FILE),
                                 timerangeImporter)
                         .ifThen(context -> ((String)context.variable("path")).endsWith(USERINPUT_FILE),
                                 userinputImporter)
                        )
                .save();
    }



    public static Importer initImport(DuneModel model) {
        if(singleton == null) {
            singleton = new Importer(model);
        }
        return singleton;
    }

    public void importFile(File folder) {
        Map<String,Object> initVar = new HashMap<>();
        initVar.put("folder",folder.getAbsolutePath());
        importTask.executeWith(duneModel.graph(), initVar, null, false,null);

        for(int t=999;t<=1071;t++) {
            System.out.println("Time: " + t);
            Execution[] executions = duneModel.findAllExecutions(0, t);
            for (int i = 0; i < executions.length; i++) {
                System.out.println(executions[i]);
                System.out.println("\tContext: " + Arrays.toString(executions[i].getContext()));
                System.out.println("\tTimeFrame: " + Arrays.toString(executions[i].getTimeframe()));
                System.out.println("\tTimeRange: " + Arrays.toString(executions[i].getTimerange()));
                System.out.println("\tUserInput: " + Arrays.toString(executions[i].getUserinput()));
            }
            System.out.println();
        }

    }


}
