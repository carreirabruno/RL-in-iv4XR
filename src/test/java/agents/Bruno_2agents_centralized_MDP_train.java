package agents;

import agents.tactics.GoalLib;
import environments.EnvironmentConfig;
import environments.LabRecruitsEnvironment;
import game.LabRecruitsTestServer;
import helperclasses.datastructures.Vec3;
import nl.uu.cs.aplib.mainConcepts.Goal;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import org.jfree.chart.ChartUtilities;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import world.BeliefState;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static agents.TestSettings.USE_SERVER_FOR_TEST;

public class Bruno_2agents_centralized_MDP_train {

    private static LabRecruitsTestServer labRecruitsTestServer;

    ArrayList<String[]> actions = new ArrayList<String[]>();
    ArrayList<String> singular_actions = new ArrayList<String>();
    ArrayList<String> existing_buttons = new ArrayList<String>();

    ArrayList<QtableObject_centralized> Qtable = new ArrayList<QtableObject_centralized>();

    ArrayList<TransitionObject_centralized> TransitionTable = new ArrayList<TransitionObject_centralized>();

    ArrayList<QtableObject_centralized> RewardTable = new ArrayList<QtableObject_centralized>();

    //    ArrayList<Number> NumberOfSteps = new ArrayList<Number>();
    ArrayList<Number> TimePerEpisode = new ArrayList<Number>();

    int episodes = 11;

    double epsilon = 1;

    double learning_rate = 0.1;
    double gamma = 0.65;

    int max_time = 10;
    long best_time = max_time;

    int early_stop_counter_reset = 3;
    int early_stop_counter = early_stop_counter_reset;


    @BeforeAll
    static void start() throws Exception {
        // Uncomment this to make the game's graphic visible:
//        TestSettings.USE_GRAPHICS = true;
        String labRecruitsExeRootDir = System.getProperty("user.dir");
        labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitsExeRootDir);
    }

    @AfterAll
    static void close() {
        if (USE_SERVER_FOR_TEST) labRecruitsTestServer.close();
    }

    /**
     * Test that the agent can train in this scenario
     */
    @Test
    public void create_policy_train(String scenario_filename, String target1, String target2) throws InterruptedException, IOException {

        System.out.println(LocalDateTime.now());

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

        this.singular_actions.add("null");
        this.singular_actions.add("button1");
        this.singular_actions.add("button2");
        this.singular_actions.add("button3");

        this.existing_buttons.add("button1");
        this.existing_buttons.add("button2");
        this.existing_buttons.add("button3");


        // Train
        for (int i = 0; i < episodes; i++) {
            System.out.println("Episode " + i + " of " + (episodes - 1) + " epsilon " + epsilon);

            var environment = new LabRecruitsEnvironment(new EnvironmentConfig("bruno_" + scenario_filename));

            // Create the agents
            var agent0 = new LabRecruitsTestAgent("agent0")
                    .attachState(new BeliefState())
                    .attachEnvironment(environment);
            agent0.setSamplingInterval(0);

            var agent1 = new LabRecruitsTestAgent("agent1")
                    .attachState(new BeliefState())
                    .attachEnvironment(environment);
            agent0.setSamplingInterval(0);

            // press play in Unity
            if (!environment.startSimulation())
                throw new InterruptedException("Unity refuses to start the Simulation!");

            // set up the initial state
            agent0.update();
            agent1.update();
            QtableObject_centralized currentState_qtableObj = new QtableObject_centralized(new State_centralized(agent0, agent1, this.existing_buttons));
            Qtable_add(currentState_qtableObj);

            double reward = 0;
            int action = getNextActionIndex(currentState_qtableObj.state, agent0, agent1);

            // Set initial goals to agents
            var g0 = doNextAction(action, 0);
            agent0.setGoal(g0);
            var g1 = doNextAction(action, 1);
            agent1.setGoal(g1);

            QtableObject_centralized nextState_qtableObj = new QtableObject_centralized(new State_centralized(agent0, agent1, this.existing_buttons));

            int stuckTicks = 0;

            long start = System.nanoTime();
            long lasTime = System.nanoTime();
            final double amountOfTicks = 5.0;  // update 5x per second
            double ns = 1000000000 / amountOfTicks;
            double delta = 0;

            while (((System.nanoTime() - start) / 1_000_000_000) < max_time) {
                long now = System.nanoTime();
                delta += (now - lasTime) / ns;
                lasTime = now;
                if (delta >= 1) {

                    var e1 = agent0.getState().worldmodel.getElement(target1);
                    var f1 = agent1.getState().worldmodel.getElement(target1);
                    var e2 = agent0.getState().worldmodel.getElement(target2);
                    var f2 = agent1.getState().worldmodel.getElement(target2);

                    if (this.actions.get(action)[0].equals("null"))
                        reward -= 0;
                    else
                        reward -= 1;

                    if (this.actions.get(action)[1].equals("null"))
                        reward -= 0;
                    else
                        reward -= 1;


                    //Update agents
                    if ((!g0.getStatus().inProgress() && !g1.getStatus().inProgress()) ) {

                        if (stuckTicks >= 5*amountOfTicks)
                            System.out.println("Stuck");


                        if (e1 != null && e1.getBooleanProperty("isOn") && this.actions.get(action)[0].equals(e1.id))
                            reward += 100;

                        else if (f1 != null && f1.getBooleanProperty("isOn") && this.actions.get(action)[1].equals(f1.id))
                            reward += 100;

                        if (e2 != null && e2.getBooleanProperty("isOn") && this.actions.get(action)[0].equals(e2.id))
                            reward += 100;

                        else if (f2 != null && f2.getBooleanProperty("isOn") && this.actions.get(action)[1].equals(f2.id))
                            reward += 100;


                        //Next state
                        nextState_qtableObj = new QtableObject_centralized(new State_centralized(agent0, agent1, this.existing_buttons));
                        Qtable_add(nextState_qtableObj);

                        //Q-learning
                        updateQtable(currentState_qtableObj, nextState_qtableObj, action, reward);


//                        //Dyna-Q
//                        QtableObject_centralized _currentState_qtableObj = new QtableObject_centralized(currentState_qtableObj);
//                        QtableObject_centralized _nextState_qtableObj = new QtableObject_centralized(nextState_qtableObj);
//                        int _action = action;
//                        double _reward = reward;
//
//                        for (int j = 0; j < 50; j++) {
//                            //          Update model
//                            //update T'[s,a,s']
//                            TransitionTable_update(_currentState_qtableObj.state, _nextState_qtableObj.state, _action);
//
//                            //udpate R'[s,a]
//                            RewardTable_update(_currentState_qtableObj, _action, _reward);
//
//                            //          Hallucinate
//                            //s = random
//                            _currentState_qtableObj = new QtableObject_centralized(this.TransitionTable.get(new Random().nextInt(this.TransitionTable.size())).currentState);
//                            //a = random
//                            _action = getPossibleAction_TransitiontTable(_currentState_qtableObj);
//                            //s' = infer from T[]
//                            _nextState_qtableObj = new QtableObject_centralized(getNextState_TransitionTable(_currentState_qtableObj));
//                            //r = infer from R[s',a]
//                            _reward = getReward_RewardTable(_currentState_qtableObj, _action);
//                            try {
//                                //          Q update
//                                updateQtable(_currentState_qtableObj, _nextState_qtableObj, _action, _reward);
//                            } catch (Exception o) {
//                            }
//
//                        }



                        currentState_qtableObj = new QtableObject_centralized(nextState_qtableObj);

                        //Action
                        action = getNextActionIndex(currentState_qtableObj.state, agent0, agent1);
                        g0 = doNextAction(action, 0);
                        agent0.setGoal(g0);
                        g0.getStatus().resetToInProgress();
                        g1 = doNextAction(action, 1);
                        agent1.setGoal(g1);
                        g1.getStatus().resetToInProgress();

                        stuckTicks = 0;

                        reward = 0;
                    }


                    // Check if the agents got stuck for too long
                    stuckTicks++;

                    // Check if the target button isOn to end the game - Tem que estar aqui para o reward ser válido
                    if ((e1 != null && e1.getBooleanProperty("isOn") || f1 != null && f1.getBooleanProperty("isOn")) &&
                            (e2 != null && e2.getBooleanProperty("isOn") || f2 != null && f2.getBooleanProperty("isOn"))) {
                        System.out.println("Objetive completed");
                        break;
                    }


                    try {
                        agent0.update();
                        agent1.update();
                    } catch (Exception ignored) {
                    }

                    delta--;
                }
            }

            long episode_time = System.nanoTime() - start;
            System.out.println(episode_time / 1_000_000_000);

            if ((i + 1) % 10 == 0)
                epsilon = 0;
            else
                epsilon = 1;

            if (i % 10 == 0 && i > 0) {
                long episodes_time_in_seconds = episode_time / 1_000_000_000;
                this.TimePerEpisode.add(episodes_time_in_seconds);
                System.out.println("added time");

                printQtable();

                //Early stop
                if (episodes_time_in_seconds < best_time) {
                    best_time = episodes_time_in_seconds;
                    System.out.println("best time = " + best_time);
                    early_stop_counter = early_stop_counter_reset;
                }

                if (episodes_time_in_seconds <= best_time+1 && best_time != max_time) {
                    early_stop_counter--;
                    System.out.println("Early stop counter = " + early_stop_counter);
                } else
                    early_stop_counter = early_stop_counter_reset;

                if (early_stop_counter == 0)
                    break;
            }

            if (!environment.close())
                throw new InterruptedException("Unity refuses to close the Simulation!");
        }

        printQtable();

        savePolicyToFile("2agents_" + scenario_filename + "_centralized_agents_Q_time");

        System.out.println(LocalDateTime.now());

        Chart example = new Chart(this.TimePerEpisode, "Centralized_Q_time_" + scenario_filename, "Time [sec]");
        example.run();
    }

    public int getNextActionIndex(State_centralized state, LabRecruitsTestAgent agent0, LabRecruitsTestAgent agent1) {
        int action_object_index = -1;
        if (new Random().nextDouble() > epsilon) {
            for (QtableObject_centralized qtableObject : this.Qtable)
                if (qtableObject.state.checkAllEquals(state))
                    action_object_index = getArgMax_double(qtableObject.actions);

        } else{

            int agent0_action_index = ThreadLocalRandom.current().nextInt(0, this.singular_actions.size());
            int agent1_action_index = ThreadLocalRandom.current().nextInt(0, this.singular_actions.size());

//            var button_agent0 = agent0.getState().worldmodel.getElement(this.singular_actions.get(agent0_action_index));
//            var button_agent1  = agent1.getState().worldmodel.getElement(this.singular_actions.get(agent1_action_index));
//
//
//            for(int i = 0; i <this.singular_actions.size(); i++) {
//                if (button_agent0 == null) {
//                    agent0_action_index = ThreadLocalRandom.current().nextInt(0, this.singular_actions.size());
//                    button_agent0 = agent0.getState().worldmodel.getElement(this.singular_actions.get(agent0_action_index));
//                }else
//                    break;
//            }
//
//            for(int i = 0; i < this.singular_actions.size(); i++) {
//                if (button_agent1 == null) {
//                    agent1_action_index = ThreadLocalRandom.current().nextInt(0, this.singular_actions.size());
//                    button_agent1 = agent1.getState().worldmodel.getElement(this.singular_actions.get(agent1_action_index));
//                }else
//                    break;
//            }

//            int agent0_action_object_index = ThreadLocalRandom.current().nextInt(0, this.singular_actions.size());
//            int agent1_action_object_index = ThreadLocalRandom.current().nextInt(0, this.singular_actions.size());
           for(int i = 0;  i< this.actions.size(); i++){
                if(this.actions.get(i)[0].equals(this.singular_actions.get(agent0_action_index)) && this.actions.get(i)[1].equals(this.singular_actions.get(agent1_action_index))){
                    action_object_index = i;
                    break;
                }
            }
//            action_object_index = ThreadLocalRandom.current().nextInt(0, this.actions.size());
        }

        return action_object_index;
    }

    public GoalStructure doNextAction(int actionIndex, int agent) {
        String[] action_object = this.actions.get(actionIndex);
        if (action_object[agent].equals("null"))
            return GoalLib.doNothing();
        else
            return GoalLib.entityIsInteracted(action_object[agent]);
    }

    public void Qtable_add(QtableObject_centralized qtableObject) {
        if (this.Qtable.size() == 0) {
            this.Qtable.add(new QtableObject_centralized(qtableObject));
        } else {
            boolean exists = false;
            for (QtableObject_centralized tempObject : this.Qtable) {
                if (tempObject.state.checkAllEquals(qtableObject.state)) {
                    exists = true;
                    break;
                }
            }
            if (!exists)
                this.Qtable.add(new QtableObject_centralized(qtableObject));
        }
    }

    public void printQtable() {
        for (QtableObject_centralized qtableObject : this.Qtable) {
            System.out.println(qtableObject.toString());
        }
    }

    public int getArgMax_double(double[] array) {
        double max = array[0];
        int maxIdx = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public double getMaxActionValue(QtableObject_centralized qtableObject) {
        double maxValue = qtableObject.actions[0];
        for (QtableObject_centralized obj : Qtable) {
            if (obj.state.checkAllEquals(qtableObject.state)) {
                for (int i = 0; i < obj.actions.length; i++)
                    if (obj.actions[i] > maxValue)
                        maxValue = obj.actions[i];
                break;
            }
        }

        return maxValue;
    }

    public double getActionValue(QtableObject_centralized qtableObject, int action) {
        for (QtableObject_centralized tempObject : this.Qtable) {
            if (qtableObject.state.checkAllEquals(tempObject.state))
                return tempObject.actions[action];
        }
        return 0;
    }

    public void updateQtableActionValue(QtableObject_centralized qtableObject, int action, double value) {
        for (QtableObject_centralized tempObject : this.Qtable) {
            if (qtableObject.state.checkAllEquals(tempObject.state)) {
                tempObject.actions[action] = value;
                break;
            }
        }
    }

    public void savePolicyToFile(String filename) {
        // save the object to file
        FileOutputStream fos = null;
        ObjectOutputStream out = null;
        try {
            fos = new FileOutputStream(filename);
            out = new ObjectOutputStream(fos);

            for (QtableObject_centralized obj : this.Qtable)
                out.writeObject(obj);

            out.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void updateQtable(QtableObject_centralized currentState, QtableObject_centralized nextState, int action, double reward) {
//        qtable[state][action] = (1 - learning_rate) * qtable[state, action] + learning_rate * (reward + gamma * Nd4j.max(qtable[next_state,:]));

        double nextStateMaxActionValue = getMaxActionValue(nextState);
        double part2 = learning_rate * (reward + gamma * nextStateMaxActionValue);
        double currentStateActionValue = getActionValue(currentState, action);
        double part1 = (1 - learning_rate) * currentStateActionValue;
        updateQtableActionValue(currentState, action, part1 + part2);
    }

    public void TransitionTable_update(State_centralized currentState, State_centralized nextState, int action) {
        if (this.TransitionTable.size() == 0) {
            this.TransitionTable.add(new TransitionObject_centralized(currentState));
            this.TransitionTable.get(0).udpateTransitionList(new QtableObject_centralized(nextState), action);
        } else {
            boolean exists = false;
            for (TransitionObject_centralized tempObject : this.TransitionTable) {
                if (tempObject.currentState.checkAllEquals(currentState)) {
                    tempObject.udpateTransitionList(new QtableObject_centralized(nextState), action);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                this.TransitionTable.add(new TransitionObject_centralized(currentState));
                this.TransitionTable.get(this.TransitionTable.size() - 1).udpateTransitionList(new QtableObject_centralized(nextState), action);
            }
        }
    }

    public int getPossibleAction_TransitiontTable(QtableObject_centralized state) {
        ArrayList<Integer> experiencedActions = new ArrayList<Integer>();

        for (TransitionObject_centralized tempObj : this.TransitionTable) {
            if (tempObj.currentState.checkAllEquals(state.state)) {
                for (QtableObject_centralized nextState : tempObj.transitions) {
                    for (int i = 0; i < nextState.actions.length; i++) {
                        if (nextState.actions[i] != 0)
                            experiencedActions.add(i);
                    }
                }
            }
        }

        int randomIndex = new Random().nextInt(experiencedActions.size());
        return experiencedActions.get(randomIndex);

    }

    public State_centralized getNextState_TransitionTable(QtableObject_centralized currentState) {
        for (TransitionObject_centralized tempObj : this.TransitionTable) {
            if (tempObj.currentState.checkAllEquals(currentState.state)) {
                for (QtableObject_centralized nextState : tempObj.transitions) {
                    for (int i = 0; i < nextState.actions.length; i++) {
                        if (nextState.actions[i] != 0)
                            return nextState.state;
//                            return tempObj.currentState;
                    }
                }
            }
        }
        return null;
    }

    public void RewardTable_update(QtableObject_centralized currentState, int action, double reward) {
        if (this.RewardTable.size() == 0) {
            this.RewardTable.add(new QtableObject_centralized(currentState));
            this.RewardTable.get(0).actions[action] = reward;
        } else {
            boolean exists = false;
            for (QtableObject_centralized tempObject : this.RewardTable) {
                if (tempObject.state.checkAllEquals(currentState.state)) {
                    tempObject.actions[action] = reward;
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                this.RewardTable.add(new QtableObject_centralized(currentState));
                this.RewardTable.get(this.RewardTable.size() - 1).actions[action] = reward;
            }
        }
    }

    public double getReward_RewardTable(QtableObject_centralized currentState, int action) {
        for (QtableObject_centralized tempObj : this.RewardTable) {
            if (tempObj.state.checkAllEquals(currentState.state)) {
                return tempObj.actions[action];
            }
        }
        return -1;
    }

}

class State_centralized implements Serializable {

    Vec3 agent1_pos;
    Vec3 agent2_pos;
    ArrayList<Integer> button_states = new ArrayList<Integer>();

    public State_centralized(LabRecruitsTestAgent agent1, LabRecruitsTestAgent agent2, ArrayList<String> actions_buttons) {
        if (agent1.getState().worldmodel.position != null)
            this.agent1_pos = new Vec3((int) agent1.getState().worldmodel.position.x, (int) agent1.getState().worldmodel.position.y, (int) agent1.getState().worldmodel.position.z);
        else
            this.agent1_pos = null;

        if (agent2.getState().worldmodel.position != null)
            this.agent2_pos = new Vec3((int) agent2.getState().worldmodel.position.x, (int) agent2.getState().worldmodel.position.y, (int) agent2.getState().worldmodel.position.z);
        else
            this.agent2_pos = null;

        //Set up the buttons state
        for(String button: actions_buttons) {

            int button_state = -1;
            var e = agent1.getState().worldmodel.getElement(button);
            var f = agent2.getState().worldmodel.getElement(button);


            if ((e != null && e.getBooleanProperty("isOn")) || (f != null && f.getBooleanProperty("isOn")))
                button_state = 1;
            else if ((e != null && !e.getBooleanProperty("isOn")) || (f != null && !f.getBooleanProperty("isOn")))
                button_state = 0;

            button_states.add(button_state);
        }
    }

    public State_centralized(Vec3 agent1_pos, Vec3 agent2_pos, ArrayList<Integer> button_states) {
        this.agent1_pos = agent1_pos;
        this.agent2_pos = agent2_pos;
        this.button_states = button_states;
    }

    public State_centralized(State_centralized state) {
        this.agent1_pos = state.agent1_pos;
        this.agent2_pos = state.agent2_pos;
        this.button_states = state.button_states;
    }

    public void print_currentState() {
        System.out.println("Agent1_pos: " + this.agent1_pos + ", Agent2_pos: " + this.agent2_pos);
        for (int i = 0; i < button_states.size(); i++)
            System.out.println("Button" + i + " state: " + button_states.get(i));
    }

    @Override
    public String toString() {
        if (this.agent1_pos == null && this.agent2_pos == null)
            return "null, null, " + button_states.toString();
        if (this.agent1_pos == null)
            return "null," + this.agent2_pos.toString() + ", " + button_states.toString();
        if (this.agent2_pos == null)
            return this.agent1_pos.toString() + ", null, " + button_states.toString();
        else
            return this.agent1_pos.toString() + ", " + this.agent2_pos.toString() + ", " + button_states.toString();
    }

    public boolean checkAllEquals2(State_centralized anotherState) {
        return (this.button_states.equals(anotherState.button_states));
    }

    public boolean checkAllEquals(State_centralized anotherState) {
        return ((this.agent1_pos == null && anotherState.agent1_pos == null ||
                (this.agent1_pos != null && anotherState.agent1_pos != null &&
                        this.agent1_pos.equals(anotherState.agent1_pos))) &&
                (this.agent2_pos == null && anotherState.agent2_pos == null ||
                        (this.agent2_pos != null && anotherState.agent2_pos != null &&
                                this.agent2_pos.equals(anotherState.agent2_pos))) &&
                this.button_states.equals(anotherState.button_states));
    }

    public boolean checkPosSimilarButtonsEquals(State_centralized anotherState, int positionDistance) {
        return ((this.agent1_pos == null && anotherState.agent1_pos == null ||
                (this.agent1_pos != null && anotherState.agent1_pos != null &&
                        this.agent1_pos.distance(anotherState.agent1_pos) < positionDistance)) &&
                (this.agent2_pos == null && anotherState.agent2_pos == null ||
                        (this.agent2_pos != null && anotherState.agent2_pos != null &&
                                this.agent2_pos.distance(anotherState.agent2_pos) < positionDistance)) &&
                this.button_states.equals(anotherState.button_states));
    }

}

class QtableObject_centralized implements Serializable {

    State_centralized state;
    double[] actions;

    public QtableObject_centralized(State_centralized state) {
        this.state = state;
        this.actions = new double[(state.button_states.size() + 1) * (state.button_states.size() + 1)];
    }

    public QtableObject_centralized(State_centralized state, double[] actions) {
        this.state = state;
        this.actions = actions;
    }

    public QtableObject_centralized(QtableObject_centralized obj) {
        this.state = obj.state;
        this.actions = obj.actions;
    }

    public QtableObject_centralized(QtableObject_centralized obj, boolean empty) {
        this.state = obj.state;
        this.actions = new double[(obj.state.button_states.size() + 1) * (obj.state.button_states.size() + 1)];
    }

    public void printQtableObject() {
        System.out.println("+++ State_Actions +++");
        state.print_currentState();
        for (int i = 0; i < actions.length; i++)
            System.out.println("Action " + i + " = " + actions[i]);
    }

    @Override
    public String toString() {
//        return "QtableObject {State=" + state.toString() + ", Actions=" + actions.toString() + "}";
        return "QtableObject {State=(" + state.toString() + ") , Actions(" + Arrays.toString(actions) + ")}";
    }
}

class TransitionObject_centralized {

    State_centralized currentState;
    ArrayList<QtableObject_centralized> transitions = new ArrayList<QtableObject_centralized>();

    public TransitionObject_centralized(State_centralized currentState) {
        this.currentState = currentState;
    }

    public void udpateTransitionList(QtableObject_centralized nextState, int action) {
        boolean exists = false;
        for (QtableObject_centralized state : transitions) {
            if (state.state.checkAllEquals(nextState.state)) {
                state.actions[action] = 1;
                exists = true;
                break;
            }
        }
        if (!exists) {
            transitions.add(new QtableObject_centralized(nextState, true));
            transitions.get(transitions.size() - 1).actions[action] = 1;
        }
    }

    @Override
    public String toString() {
        return "TransitionTable {State=(" + currentState.toString() + ") , Transitions(" + transitions.toString() + ")}";
    }

}