package fr.spirals.snt.dune.mwdb;

import org.mwg.Callback;
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
    /*public static final String CONTEXT_FILE = "context.csv";
    public static final String METRICS_FILE = "metrics.csv";
    public static final String TIMERANGE_FILE = "timerange.csv";
    public static final String USERINPUT_FILE = "userinput.csv";

    public static final String CSV_SEP = ",";

    private static final CSVImporter contextImporter = new CSVImporter();
    private static final CSVImporter metricsImporter = new CSVImporter();
    private static final CSVImporter timerangeImporter = new CSVImporter();
    private static final CSVImporter userinputImporter = new CSVImporter();
    private static Importer singleton;

    private Importer(){
        contextImporter.setSeparator(CSV_SEP);
        contextImporter.mapper().extractTime("{time}","ss");
        contextImporter.mapper().field("device");
        contextImporter.mapper().field("SDK_version").rename("sdk_version");
        contextImporter.mapper().field("API_level").rename("api_level");
        contextImporter.mapper().field("screen_res");
        contextImporter.mapper().field("cpu_api");
        contextImporter.mapper().field("net_type");

        metricsImporter.setSeparator(CSV_SEP);
        metricsImporter.mapper().field("timeFrame").isDouble();

        timerangeImporter.setSeparator(CSV_SEP);
        timerangeImporter.mapper().field("startTimestamp");
        timerangeImporter.mapper().field("endTimestamp");


        userinputImporter.setSeparator(CSV_SEP);
        userinputImporter.mapper().field("timestamp").isDouble();
        userinputImporter.mapper().field("eventType");
        userinputImporter.mapper().field("viewId");

    }

    public static void initImport() {
        if(singleton == null) {
            singleton = new Importer();
        }
    }

    public static void importFile(Graph graph, File folder) {
        if(folder.exists()) {
            File[] files = folder.listFiles();
            for(int i=0;i<files.length;i++) {
                if(files[i].getName().equals(CONTEXT_FILE)) {
                    Node context = graph.newNode(0,-3600000);
                    try {
                        contextImporter.importToNode(files[i], context, null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println(files[i].getName());
                }
            }
        }
    }*/

    public static final String CONTEXT_FILE = "context.csv";
    public static final String METRICS_FILE = "metrics.csv";
    public static final String TIMERANGE_FILE = "timerange.csv";
    public static final String USERINPUT_FILE = "userinput.csv";

    public static final String CSV_SEP = ",";

//    private static Task initTask;
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
                .action(ImporterActions.READLINES,"{{path}}")
                .foreach(
                        selectObject(line -> !((String)line).startsWith("device")  && !((String)line).isEmpty())
                                .foreach(
                                        split(",")
                                                .then(taskContext -> {
                                                    String[] res = taskContext.resultAsStringArray();
                                                    Context executionCtxt = duneModel.newContext(0,0);
                                                    executionCtxt.setDevice(res[0]);
                                                    executionCtxt.setSdk_version(res[1]);
                                                    executionCtxt.setApi_level(res[2]);
                                                    executionCtxt.setScreen_res(res[3]);
                                                    executionCtxt.setCpu_abi(res[4]);
                                                    executionCtxt.setNet_type(res[5]);
                                                    taskContext.setResult(executionCtxt);
                                                })
                                                .asVar("taskContext")
                                                .fromVar("executionNode")
                                                .add(Execution.CONTEXT,"taskContext")
                                )
                );

        metricsImporter
                .setVar("isFirst",true)
                .setVar("counter",0)
                .action(ImporterActions.READLINES,"{{path}}")
                .foreach(selectObject(line -> !((String)line).startsWith("timeFrame") && !((String)line).isEmpty())
                        .foreach(
                                then(taskContext -> {
                                    String res = taskContext.resultAsString();
                                    boolean isFirst = (boolean) taskContext.variable("isFirst");
                                    int counter = (int) taskContext.variable("counter");

                                    Execution execution = (Execution) taskContext.variable("executionNode");
                                    if(isFirst) {
                                        taskContext.setVariable("isFirst",false);
                                        Metrics metrics = duneModel.newMetrics(0,counter);
                                        metrics.setTimeframe(Double.valueOf(res));
                                        execution.addToTimeframe(metrics);
                                    } else {
                                        Metrics metrics = execution.getTimeframe()[0];
                                        metrics.jump(counter, new Callback<Metrics>() {
                                            @Override
                                            public void on(Metrics future) {
                                                future.setTimeframe(Double.valueOf(res));
                                            }
                                        });
                                    }

                                    taskContext.setVariable("counter",++counter);
                                    taskContext.setResult(null);
                                })
                        )
                );

        timerangeImporter
                .action(ImporterActions.READLINES,"{{path}}")
                .foreach(selectObject(oLine -> !((String)oLine).startsWith("st") && !((String)oLine).isEmpty())
                            .foreach(
                                    split(",")
                                            .then(taskContext -> {
                                                String[] res = taskContext.resultAsStringArray();
                                                TimeRange timeRange = duneModel.newTimerange(0,0);
                                                timeRange.setStarttime(Double.valueOf(res[0]));
                                                timeRange.setEndtime(Double.valueOf(res[1]));
                                                taskContext.setResult(timeRange);
                                            })
                                            .asVar("timeRange")
                                            .fromVar("executionNode")
                                            .add(Execution.TIMERANGE,"timeRange")
                            )
                );


        userinputImporter
                .action(ImporterActions.READLINES,"{{path}}")
                .foreach(selectObject(oLine -> !((String)oLine).startsWith("timestamp") && !((String)oLine).isEmpty())
                            .foreach(
                                    split(",")
                                            .then(taskContext -> {
                                                String[] res = taskContext.resultAsStringArray();
                                                UserInput userInput = duneModel.newUserinput(0,Long.valueOf(res[0]));
                                                userInput.setEventtype(res[1]);
                                                userInput.setViewid(res[2]);
                                                taskContext.setResult(userInput);
                                            })
                                            .asVar("userInput")
                                            .fromVar("executionNode")
                                            .add(Execution.USERINPUT,"userInput")
                                            .print("{{result}}")
                            )
                );



        importTask
                .then(taskContext -> {
                    Execution execution = duneModel.newExecution(0,0);
                    execution.setIdexecution("execution1"); //fixme
                    taskContext.setResult(execution);
                })
                .asVar("executionNode")
                .action(ImporterActions.READFILES, "{{folder}}")
                .foreach(
                         asVar("path")
                         .ifThen(context -> ((String)context.variable("path")).endsWith(CONTEXT_FILE),
                                        contextImporter)
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

        Execution[] executions = duneModel.findAllExecutions(0,1001);
        for(int i=0;i<executions.length;i++) {
            System.out.println(executions[i]);
            System.out.println("\tContext: " + Arrays.toString(executions[i].getContext()));
            System.out.println("\tTimeFrame: " + Arrays.toString(executions[i].getTimeframe()));
//            System.out.println("\t\t" + executions[i].getTimeframe()[0].getTimeframe());
            System.out.println("\tTimeRange: " + Arrays.toString(executions[i].getTimerange()));
            System.out.println("\tUserInput: " + Arrays.toString(executions[i].getUserinput()));
        }
    }


}
