package agents;

import agents.tactics.GoalLib;
import alice.tuprolog.Agent;
import environments.EnvironmentConfig;
import environments.LabRecruitsEnvironment;
import game.LabRecruitsTestServer;
import helperclasses.datastructures.Tuple;
import helperclasses.datastructures.Vec3;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import world.BeliefState;

import java.io.*;
import java.util.ArrayList;

import static agents.TestSettings.USE_SERVER_FOR_TEST;


public class Bruno_2agents_centralized_MDP_test {

    private static LabRecruitsTestServer labRecruitsTestServer = null;

    ArrayList<String[]> actions = new ArrayList<String[]>();
    ArrayList<String> existing_buttons = new ArrayList<String>();

    ArrayList<QtableObject_centralized> policy = new ArrayList<QtableObject_centralized>();

    @BeforeAll
    static void start() {
        // Uncomment this to make the game's graphic visible:
        TestSettings.USE_GRAPHICS = true;
        String labRecruitsExeRootDir = System.getProperty("user.dir");
        labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitsExeRootDir);
    }

    @AfterAll
    static void close() {
        if (labRecruitsTestServer != null) labRecruitsTestServer.close();
    }

    /**
     * Test that the agent can test this scenario
     */
    @Test
  public Tuple<ArrayList<Vec3>, ArrayList<Vec3>> run(String scenario_filename, String target1, String target2) throws InterruptedException, IOException {


        // Set the possible buttons to act
        this.actions.add(new String[]{"null", "null"});
        this.actions.add(new String[]{"null", "button1"});
        this.actions.add(new String[]{"null", "button2"});
        this.actions.add(new String[]{"null", "button3"});
        this.actions.add(new String[]{"button1", "null"});
        this.actions.add(new String[]{"button1", "button1"});
        this.actions.add(new String[]{"button1", "button2"});
        this.actions.add(new String[]{"button1", "button3"});
        this.actions.add(new String[]{"button2", "null"});
        this.actions.add(new String[]{"button2", "button1"});
        this.actions.add(new String[]{"button2", "button2"});
        this.actions.add(new String[]{"button2", "button3"});
        this.actions.add(new String[]{"button3", "null"});
        this.actions.add(new String[]{"button3", "button1"});
        this.actions.add(new String[]{"button3", "button2"});
        this.actions.add(new String[]{"button3", "button3"});

        this.existing_buttons.add("button1");
        this.existing_buttons.add("button2");
        this.existing_buttons.add("button3");

        // Get the policy for the agents to use
        getPolicy("2agents_" + scenario_filename + "_centralized_agents_Q_time");

        var environment = new LabRecruitsEnvironment(new EnvironmentConfig("bruno_" + scenario_filename));

        // Create the agent
        var agent0 = new LabRecruitsTestAgent("agent0")
                .attachState(new BeliefState())
                .attachEnvironment(environment);
        agent0.setSamplingInterval(0);

        var agent1 = new LabRecruitsTestAgent("agent1")
                .attachState(new BeliefState())
                .attachEnvironment(environment);
        agent1.setSamplingInterval(0);


        // press play in Unity
        if (!environment.startSimulation())
            throw new InterruptedException("Unity refuses to start the Simulation!");

        // set up the initial state
        agent0.update();
        agent1.update();
        State_centralized currentState = new State_centralized(agent0, agent1, this.existing_buttons);

        int action = getNextActionIndex(currentState);

        // Set initial goals to agents
        var g0 = doNextAction(action, 0);
        agent0.setGoal(g0);
        var g1 = doNextAction(action, 1);
        agent1.setGoal(g1);

        long start = System.nanoTime();
        ArrayList<String[]> pressedButtons = new ArrayList<String[]>();


        ArrayList<Vec3> agent0Positions = new ArrayList<Vec3>();
        ArrayList<Vec3> agent1Positions = new ArrayList<Vec3>();

        while (true) {

            addToPos(agent0.getState().worldmodel.position, agent0Positions);
            addToPos(agent1.getState().worldmodel.position, agent1Positions);

            var e1 = agent0.getState().worldmodel.getElement(target1);
            var f1 = agent1.getState().worldmodel.getElement(target1);
            var e2 = agent0.getState().worldmodel.getElement(target2);
            var f2 = agent1.getState().worldmodel.getElement(target2);

            // Check if the target button isOn to end the game
            if ((e1 != null && e1.getBooleanProperty("isOn") || f1 != null && f1.getBooleanProperty("isOn")) &&
                    (e2 != null && e2.getBooleanProperty("isOn") || f2 != null && f2.getBooleanProperty("isOn"))) {

                if (g0.getStatus().success())
                    pressedButtons.add(new String[]{"Agent0", this.actions.get(action)[0]});

                if (g1.getStatus().success())
                    pressedButtons.add(new String[]{"Agent1", this.actions.get(action)[1]});

                System.out.println("Objetive completed");
                break;
            }

            if (!g0.getStatus().inProgress() && !g1.getStatus().inProgress()) {
                if (g0.getStatus().success())
                    pressedButtons.add(new String[]{"Agent0", this.actions.get(action)[0]});
                ;
                if (g1.getStatus().success())
                    pressedButtons.add(new String[]{"Agent1", this.actions.get(action)[1]});


                currentState = new State_centralized(agent0, agent1, this.existing_buttons);

                // Set up the next action - agent0
                action = getNextActionIndex(currentState);
                g0 = doNextAction(action, 0);
                agent0.setGoal(g0);
                g0.getStatus().resetToInProgress();
                // Set up the next action - agent1
                g1 = doNextAction(action, 1);
                agent1.setGoal(g1);
                g1.getStatus().resetToInProgress();

            }

            try {
                agent0.update();
                agent1.update();
            } catch (Exception wtf) {
            }

        }


        long finish = System.nanoTime();
        long totalTime = (finish - start) / 1_000_000_000;

        if (!environment.close())
            throw new InterruptedException("Unity refuses to close the Simulation!");

        System.out.println("Time " + totalTime);


        return new Tuple(agent0Positions, agent1Positions);

    }

    public int getNextActionIndex(State_centralized state) {
        for (QtableObject_centralized qtableObject : this.policy)
            if (qtableObject.state.checkAllEquals(state))
                return getArgMax_double(qtableObject.actions);

        return 0;
    }

    public GoalStructure doNextAction(int actionIndex, int agent) {
        String[] action_object = this.actions.get(actionIndex);
        if (action_object[agent].equals("null"))
            return GoalLib.doNothing();
        else
            return GoalLib.entityIsInteracted(action_object[agent]);
    }

    public int getArgMax_double(double[] array) {
        double max = array[0];
        int maxIdx = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] >= max) {
                max = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public void getPolicy(String filename) throws IOException {
        // read the object from file
        FileInputStream fis = null;
        ObjectInputStream in = null;
        try {
            Object obj;
            fis = new FileInputStream(filename);
            in = new ObjectInputStream(fis);
            for (; ; ) {
                obj = in.readObject();
                this.policy.add((QtableObject_centralized) obj);
//                System.out.println(obj.toString());
            }

        } catch (Exception ignored) {
//            System.out.println(i.toString());
        }

        assert in != null;

    }

    public void addToPos(Vec3 agentPos, ArrayList<Vec3> list) {
        if (agentPos != null) {
            if (list.size() == 0)
                list.add(agentPos);
            boolean equal = false;
            for (Vec3 temp : list)
                if (temp.equals(agentPos) || (temp.x == agentPos.x && temp.z == agentPos.z)) {
                    equal = true;
                    break;
                }
            if (!equal)
                list.add(agentPos);
        }

    }

}
