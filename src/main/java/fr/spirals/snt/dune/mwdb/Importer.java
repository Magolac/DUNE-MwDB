package fr.spirals.snt.dune.mwdb;

import org.mwg.Callback;
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
                                                .setVar("taskContext",null)
                                )
                )
                .;

        metricsImporter
                .setTime("{{startTime}}")
                .setVar("isFirst",true)
                .setVar("counter","{{startTime}}")
                .action(ImporterActions.READLINES,"{{path}}")
                .selectObject(line -> !((String)line).startsWith("timeFrame") && !((String)line).isEmpty())
                        .foreach(
                                then(taskContext -> {
                                    String res = taskContext.resultAsString();
                                    boolean isFirst = (boolean) taskContext.variable("isFirst");
                                    int counter = Integer.parseInt((String) taskContext.variable("counter"));

                                    Execution execution = (Execution) taskContext.variable("executionNode");
                                    if(isFirst) {
                                        taskContext.setVariable("isFirst",false);
                                        Metrics metrics = duneModel.newMetrics(0,counter);
                                        metrics.setTimeframe(Double.valueOf(res));
                                        execution.jump(counter, new Callback<Execution>() {
                                            @Override
                                            public void on(Execution jumped) {
                                                jumped.addToTimeframe(metrics);
                                            }
                                        });

                                    } else {
                                        execution.jump(counter, new Callback<Execution>() {
                                            @Override
                                            public void on(Execution jumped) {
                                                Metrics metrics = jumped.getTimeframe()[0];
                                                metrics.jump(counter, new Callback<Metrics>() {
                                                    @Override
                                                    public void on(Metrics future) {
                                                        future.setTimeframe(Double.valueOf(res));
                                                    }
                                                });
                                            }
                                        });

                                    }

                                    taskContext.setVariable("counter",counter + Integer.parseInt(res) + "");
                                    taskContext.setResult(null);
                                })
                        );

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
                .foreach(selectObject(oLine -> !((String)oLine).startsWith("timestamp") && !((String)oLine).isEmpty())
                            .foreach(
                                    split(",")
                                            /*.then(taskContext -> {
                                                String[] res = taskContext.resultAsStringArray();
                                                UserInput userInput = duneModel.newUserinput(0,Long.valueOf(res[0]));
                                                userInput.setEventtype(res[1]);
                                                userInput.setViewid(res[2]);
                                                taskContext.setResult(userInput);
                                            })*/
                                            .asVar("res")
                                            .newTypedNode(UserInput.NODE_NAME)
                                            .setProperty(UserInput.EVENTTYPE,Type.STRING,"{{res[1]}}")
                                            .setProperty(UserInput.VIEWID,Type.STRING,"{{res[2]}}")
                                            .asVar("userInput")
                                            .fromVar("executionNode")
                                            .add(Execution.USERINPUT,"userInput")
                            )
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

        /*duneModel.graph().lookup(0, 0, n.id(), new Callback<Node>() {
            @Override
            public void on(Node result) {
                System.out.println(result.getClass());
            }
        });*/

//        print("{{= 1000 + 12}}")
//                .execute(duneModel.graph(),null);




    }


}
