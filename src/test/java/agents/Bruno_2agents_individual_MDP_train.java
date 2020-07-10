package agents;

import agents.tactics.GoalLib;
import environments.EnvironmentConfig;
import environments.LabRecruitsEnvironment;
import game.LabRecruitsTestServer;
import helperclasses.datastructures.Vec3;
import nl.uu.cs.aplib.mainConcepts.GoalStructure;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import world.BeliefState;

import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static agents.TestSettings.USE_SERVER_FOR_TEST;

public class Bruno_2agents_individual_MDP_train {

    private static LabRecruitsTestServer labRecruitsTestServer;

    ArrayList<String> actions = new ArrayList<String>();
    ArrayList<String> existing_buttons = new ArrayList<String>();

    ArrayList<QtableObject_individual> Qtable_agent0 = new ArrayList<QtableObject_individual>();
    ArrayList<QtableObject_individual> Qtable_agent1 = new ArrayList<QtableObject_individual>();

    ArrayList<TransitionObject_individual> TransitionTable_agent0 = new ArrayList<TransitionObject_individual>();
    ArrayList<TransitionObject_individual> TransitionTable_agent1 = new ArrayList<TransitionObject_individual>();

    ArrayList<QtableObject_individual> RewardTable_agent0 = new ArrayList<QtableObject_individual>();
    ArrayList<QtableObject_individual> RewardTable_agent1 = new ArrayList<QtableObject_individual>();

    ArrayList<Number> TimePerEpisode = new ArrayList<Number>();

    int episodes = 1001;

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
        this.actions.add("null");
        this.actions.add("button1");
        this.actions.add("button2");
        this.actions.add("button3");

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

            // set up the initial states
            agent0.update();
            QtableObject_individual currentState_qtableObj_agent0 = new QtableObject_individual(new State_individual(agent0, this.existing_buttons));
            Qtable_add(this.Qtable_agent0, currentState_qtableObj_agent0);

            double reward_agent0 = 0;
            int action_agent0 = getNextActionIndex(this.Qtable_agent0, currentState_qtableObj_agent0.state);

            agent1.update();
            QtableObject_individual currentState_qtableObj_agent1 = new QtableObject_individual(new State_individual(agent1, this.existing_buttons));
            Qtable_add(this.Qtable_agent1, currentState_qtableObj_agent1);

            double reward_agent1 = 0;
            int action_agent1 = getNextActionIndex(this.Qtable_agent1, currentState_qtableObj_agent1.state);


            // Set initial goals to agents
            var g0 = doNextAction(action_agent0);
            agent0.setGoal(g0);
            var g1 = doNextAction(action_agent1);
            agent1.setGoal(g1);

            QtableObject_individual nextState_qtableObj_agent0 = new QtableObject_individual(new State_individual(agent0, this.existing_buttons));

            QtableObject_individual nextState_qtableObj_agent1 = new QtableObject_individual(new State_individual(agent1, this.existing_buttons));

            int stuckTicks_agent0 = 0;
            int stuckTicks_agent1 = 0;

            long start = System.nanoTime();
            long lasTime = System.nanoTime();
            final double amountOfTicks = 5.0;  //update 5x per second
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


                    if (g0.getStatus().inProgress()) {
                        if (action_agent0 == 0)
                            reward_agent0 -= 0;
                        else
                            reward_agent0 -= 1;
                    }
                    if (g1.getStatus().inProgress()) {
                        if (action_agent1 == 0)
                            reward_agent1 -= 0;
                        else
                            reward_agent1 -= 1;
                    }

                    // Agents
                if (!g0.getStatus().inProgress() && !g1.getStatus().inProgress()) {


                        if(stuckTicks_agent0 >= (5 * amountOfTicks))
                            System.out.println("agent0 stuck");
                        if(stuckTicks_agent1 >= (5 * amountOfTicks))
                            System.out.println("agent1 stuck");

                        // Agent0

                        //Reward
                        if ((e1 != null && e1.getBooleanProperty("isOn") && this.actions.get(action_agent0).equals(e1.id)) ||
                                (e2 != null && e2.getBooleanProperty("isOn") && this.actions.get(action_agent0).equals(e2.id)))
                            reward_agent0 += 100;

                        //Next state
                        nextState_qtableObj_agent0 = new QtableObject_individual(new State_individual(agent0, this.existing_buttons));
                        Qtable_add(this.Qtable_agent0, nextState_qtableObj_agent0);

                        //Q-learning
                        updateQtable(this.Qtable_agent0, currentState_qtableObj_agent0, nextState_qtableObj_agent0, action_agent0, reward_agent0);

                        
//                        //Dyna-Q
//                        QtableObject_individual _currentState_qtableObj_agent0 = new QtableObject_individual(currentState_qtableObj_agent0);
//                        QtableObject_individual _nextState_qtableObj_agent0 = new QtableObject_individual(nextState_qtableObj_agent0);
//                        int _action_agent0 = action_agent0;
//                        double _reward_agent0 = reward_agent0;
//
//                        for (int j = 0; j < 50; j++) {
//                            //          Update model
//                            //update T'[s,a,s']
//                            TransitionTable_update(this.TransitionTable_agent0, _currentState_qtableObj_agent0.state, _nextState_qtableObj_agent0.state, _action_agent0);
//
//                            //udpate R'[s,a]
//                            RewardTable_update(this.RewardTable_agent0, _currentState_qtableObj_agent0.state, _action_agent0, _reward_agent0);
//
//                            //          Hallucinate
//                            //s = random
//                            _currentState_qtableObj_agent0 = new QtableObject_individual(this.TransitionTable_agent0.get(new Random().nextInt(this.TransitionTable_agent0.size())).currentState);
//                            //a = random
//                            _action_agent0 = getPossibleAction_TransitiontTable(this.TransitionTable_agent0, _currentState_qtableObj_agent0);
//                            //s' = infer from T[]
//                            _nextState_qtableObj_agent0 = new QtableObject_individual(getNextState_TransitionTable(this.TransitionTable_agent0, _currentState_qtableObj_agent0));
//                            //r = infer from R[s',a]
//                            _reward_agent0 = getReward_RewardTable(this.RewardTable_agent0, _currentState_qtableObj_agent0, _action_agent0);
//                            try {
//                                //          Q update
//                                updateQtable(this.Qtable_agent0, _currentState_qtableObj_agent0, _nextState_qtableObj_agent0, _action_agent0, _reward_agent0);
//                            } catch (Exception o) {
//                            }
//                        }


                        
                        currentState_qtableObj_agent0 = new QtableObject_individual(nextState_qtableObj_agent0);

                        //Action
                        action_agent0 = getNextActionIndex(this.Qtable_agent0, currentState_qtableObj_agent0.state);
                        g0 = doNextAction(action_agent0);
                        agent0.setGoal(g0);
                        g0.getStatus().resetToInProgress();
                        stuckTicks_agent0 = 0;

                        reward_agent0 = 0;


                        // Agent1

                        //Reward
                        if ((f1 != null && f1.getBooleanProperty("isOn") && this.actions.get(action_agent1).equals(f1.id)) ||
                                (f2 != null && f2.getBooleanProperty("isOn") && this.actions.get(action_agent1).equals(f2.id)))
                            reward_agent1 += 100;

                        //Next state
                        nextState_qtableObj_agent1 = new QtableObject_individual(new State_individual(agent1, this.existing_buttons));
                        Qtable_add(this.Qtable_agent1, nextState_qtableObj_agent1);

                        //Q-learning
                        updateQtable(this.Qtable_agent1, currentState_qtableObj_agent1, nextState_qtableObj_agent1, action_agent1, reward_agent1);

//                        //Dyna-Q
//                        QtableObject_individual _currentState_qtableObj_agent1 = new QtableObject_individual(currentState_qtableObj_agent1);
//                        QtableObject_individual _nextState_qtableObj_agent1 = new QtableObject_individual(nextState_qtableObj_agent1);
//                        int _action_agent1 = action_agent1;
//                        double _reward_agent1 = reward_agent1;
//
//                        for (int j = 0; j < 50; j++) {
//                            //          Update model
//                            //update T'[s,a,s']
//                            TransitionTable_update(this.TransitionTable_agent1, _currentState_qtableObj_agent1.state, _nextState_qtableObj_agent1.state, _action_agent1);
//
//                            //udpate R'[s,a]
//                            RewardTable_update(this.RewardTable_agent1, _currentState_qtableObj_agent1.state, _action_agent1, _reward_agent1);
//
//                            //          Hallucinate
//                            //s = random
//                            _currentState_qtableObj_agent1 = new QtableObject_individual(this.TransitionTable_agent1.get(new Random().nextInt(this.TransitionTable_agent1.size())).currentState);
//                            //a = random
//                            _action_agent1 = getPossibleAction_TransitiontTable(this.TransitionTable_agent1, _currentState_qtableObj_agent1);
//                            //s' = infer from T[]
//                            _nextState_qtableObj_agent1 = new QtableObject_individual(getNextState_TransitionTable(this.TransitionTable_agent1, _currentState_qtableObj_agent1));
//                            //r = infer from R[s',a]
//                            _reward_agent1 = getReward_RewardTable(this.RewardTable_agent1, _currentState_qtableObj_agent1, _action_agent1);
//                            try {
//                                //          Q update
//                                updateQtable(this.Qtable_agent1, _currentState_qtableObj_agent1, _nextState_qtableObj_agent1, _action_agent1, _reward_agent1);
//                            } catch (Exception o) {
//                            }
//                        }
                        
                        
                        currentState_qtableObj_agent1 = new QtableObject_individual(nextState_qtableObj_agent1);

                        //Action
                        action_agent1 = getNextActionIndex(this.Qtable_agent1, currentState_qtableObj_agent1.state);
                        g1 = doNextAction(action_agent1);
                        agent1.setGoal(g1);
                        g1.getStatus().resetToInProgress();
                        stuckTicks_agent1 = 0;

                        reward_agent1 = 0;

                    }

                    // Check if the agents got stuck for too long
                    stuckTicks_agent0++;
                    stuckTicks_agent1++;

                    // Check if the target button isOn to end the game
                  if ((e1 != null && e1.getBooleanProperty("isOn") || f1 != null && f1.getBooleanProperty("isOn")) &&
                            (e2 != null && e2.getBooleanProperty("isOn") || f2 != null && f2.getBooleanProperty("isOn"))) {
                        System.out.println("Objetive completed");
                        break;
                    }


                    try {
                        agent0.update();
                        agent1.update();
                    } catch (Exception ignored) {   }

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
                printQtable(this.Qtable_agent0);
                System.out.println();
                printQtable(this.Qtable_agent1);


                //Early stop
                if (episodes_time_in_seconds < best_time) {
                    best_time = episodes_time_in_seconds;
                    System.out.println("best time = " + best_time);
                    early_stop_counter = early_stop_counter_reset;
                }

                if (episodes_time_in_seconds <= best_time + 1 && best_time != max_time && best_time+1 != max_time) {
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

        savePoliciesToFile(this.Qtable_agent0, "2agents_" + scenario_filename + "_individual_agent0_Q_time");
        savePoliciesToFile(this.Qtable_agent1, "2agents_" + scenario_filename + "_individual_agent1_Q_time");

        System.out.println(LocalDateTime.now());

        Chart example = new Chart(this.TimePerEpisode, "Individual_Q_time_" + scenario_filename, "Time [sec]");
        example.run();
    }

    public int getNextActionIndex(ArrayList<QtableObject_individual> Qtable, State_individual state) {
        int action_object_index = -1;
        if (new Random().nextDouble() > epsilon) {
            for (QtableObject_individual qtableObject : Qtable)
                if (qtableObject.state.checkAllEquals(state))
                    action_object_index = getArgMax_double(qtableObject.actions);

        } else
            action_object_index = ThreadLocalRandom.current().nextInt(0, this.actions.size());

        return action_object_index;
    }

    public GoalStructure doNextAction(int actionIndex) {
        String action_object = this.actions.get(actionIndex);
        if (action_object.equals("null"))
            return GoalLib.doNothing();
        else
            return GoalLib.entityIsInteracted(action_object);
    }

    public void Qtable_add(ArrayList<QtableObject_individual> Qtable, QtableObject_individual qtableObject) {
        if (Qtable.size() == 0) {
            Qtable.add(new QtableObject_individual(qtableObject));
        } else {
            boolean exists = false;
            for (QtableObject_individual tempObject : Qtable) {
                if (tempObject.state.checkAllEquals(qtableObject.state)) {
                    exists = true;
                    break;
                }
            }
            if (!exists)
                Qtable.add(new QtableObject_individual(qtableObject));
        }
    }

    public void printQtable(ArrayList<QtableObject_individual> Qtable) {
        for (QtableObject_individual qtableObject : Qtable) {
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

    public double getMaxActionValue(ArrayList<QtableObject_individual> Qtable, QtableObject_individual qtableObject) {
        double maxValue = qtableObject.actions[0];
        for (QtableObject_individual obj : Qtable) {
            if (obj.state.checkAllEquals(qtableObject.state)) {
                for (int i = 0; i < obj.actions.length; i++)
                    if (obj.actions[i] > maxValue)
                        maxValue = obj.actions[i];
                break;
            }
        }

        return maxValue;
    }

    public double getActionValue(ArrayList<QtableObject_individual> Qtable, QtableObject_individual qtableObject, int action) {
        for (QtableObject_individual tempObject : Qtable) {
            if (qtableObject.state.checkAllEquals(tempObject.state))
                return tempObject.actions[action];
        }
        return 0;
    }

    public void updateQtableActionValue(ArrayList<QtableObject_individual> Qtable, QtableObject_individual qtableObject, int action, double value) {
        for (QtableObject_individual tempObject : Qtable) {
            if (qtableObject.state.checkAllEquals(tempObject.state)) {
                tempObject.actions[action] = value;
                break;
            }
        }
    }

    public void savePoliciesToFile(ArrayList<QtableObject_individual> Qtable, String filename) {
        // save the object to file
        FileOutputStream fos = null;
        ObjectOutputStream out = null;
        try {
            fos = new FileOutputStream(filename);
            out = new ObjectOutputStream(fos);

            for (QtableObject_individual obj : Qtable)
                out.writeObject(obj);

            out.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public void updateQtable(ArrayList<QtableObject_individual> Qtable, QtableObject_individual currentState, QtableObject_individual nextState, int action, double reward) {
//        qtable[state][action] = (1 - learning_rate) * qtable[state, action] + learning_rate * (reward + gamma * Nd4j.max(qtable[next_state,:]));

        double nextStateMaxActionValue = getMaxActionValue(Qtable, nextState);
        double part2 = learning_rate * (reward + gamma * nextStateMaxActionValue);
        double currentStateActionValue = getActionValue(Qtable, currentState, action);
        double part1 = (1 - learning_rate) * currentStateActionValue;
        updateQtableActionValue(Qtable, currentState, action, part1 + part2);
    }

    public void RewardTable_update(ArrayList<QtableObject_individual> RewardTable, State_individual currentState, int action, double reward) {
        if (RewardTable.size() == 0) {
            RewardTable.add(new QtableObject_individual(currentState));
            RewardTable.get(0).actions[action] = reward;
        } else {
            boolean exists = false;
            for (QtableObject_individual tempObject : RewardTable) {
                if (tempObject.state.checkAllEquals(currentState)) {
                    tempObject.actions[action] = reward;
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                RewardTable.add(new QtableObject_individual(currentState));
                RewardTable.get(RewardTable.size() - 1).actions[action] = reward;
            }
        }
    }

    public double getReward_RewardTable(ArrayList<QtableObject_individual> RewardTable, QtableObject_individual currentState, int action) {
        for (QtableObject_individual tempObj : RewardTable) {
            if (tempObj.state.checkAllEquals(currentState.state)) {
                return tempObj.actions[action];
            }
        }
        return -1;
    }

    public void TransitionTable_update(ArrayList<TransitionObject_individual> TransitionTable, State_individual currentState, State_individual nextState, int action) {
        if (TransitionTable.size() == 0) {
            TransitionTable.add(new TransitionObject_individual(currentState));
            TransitionTable.get(0).udpateTransitionList(new QtableObject_individual(nextState), action);
        } else {
            boolean exists = false;
            for (TransitionObject_individual tempObject : TransitionTable) {
                if (tempObject.currentState.checkAllEquals(currentState)) {
                    tempObject.udpateTransitionList(new QtableObject_individual(nextState), action);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                TransitionTable.add(new TransitionObject_individual(currentState));
                TransitionTable.get(TransitionTable.size() - 1).udpateTransitionList(new QtableObject_individual(nextState), action);
            }
        }
    }

    public int getPossibleAction_TransitiontTable(ArrayList<TransitionObject_individual> TransitionTable, QtableObject_individual state) {
        ArrayList<Integer> experiencedActions = new ArrayList<Integer>();

        for (TransitionObject_individual tempObj : TransitionTable) {
            if (tempObj.currentState.checkAllEquals(state.state)) {
                for (QtableObject_individual nextState : tempObj.transitions) {
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

    public State_individual getNextState_TransitionTable(ArrayList<TransitionObject_individual> TransitionTable, QtableObject_individual currentState) {
        for (TransitionObject_individual tempObj : TransitionTable) {
            if (tempObj.currentState.checkAllEquals(currentState.state)) {
                for (QtableObject_individual nextState : tempObj.transitions) {
                    for (int i = 0; i < nextState.actions.length; i++) {
                        if (nextState.actions[i] != 0)
                            return nextState.state;
                    }
                }
            }
        }
        return null;
    }

    public void printQTableObject(ArrayList<QtableObject_individual> Qtable, QtableObject_individual state) {
        for (QtableObject_individual qtableObject : Qtable)
            if (qtableObject.state.checkAllEquals(state.state))
                System.out.println(qtableObject.toString());
    }

}

class State_individual implements Serializable {

    Vec3 agent_pos;
    ArrayList<Integer> button_states = new ArrayList<Integer>();

    public State_individual(LabRecruitsTestAgent agent, ArrayList<String> actions) {
        //Agent pos
        if (agent.getState().worldmodel.position != null)
            this.agent_pos = new Vec3((int) agent.getState().worldmodel.position.x, (int) agent.getState().worldmodel.position.y, (int) agent.getState().worldmodel.position.z);
        else
            this.agent_pos = null;

        //Set up the buttons state
        for (String button : actions) {
            int button_state = -1;
            var e = agent.getState().worldmodel.getElement(button);

            if (e != null && e.getBooleanProperty("isOn"))
                button_state = 1;
            else if (e != null && e.getBooleanProperty("isOff"))
                button_state = 0;

            button_states.add(button_state);
        }
    }

    public State_individual(Vec3 agent_pos, ArrayList<Integer> button_states) {
        this.agent_pos = agent_pos;
        this.button_states = button_states;
    }

    public State_individual(State_individual state) {
        this.agent_pos = state.agent_pos;
        this.button_states = state.button_states;
    }

    public void print_currentState() {
        System.out.println("Agent_pos: " + agent_pos);
        for (int i = 0; i < button_states.size(); i++)
            System.out.println("Button" + i + " state: " + button_states.get(i));
    }

    @Override
    public String toString() {
        if (this.agent_pos == null)
            return "null, " + button_states.toString();
        else
            return this.agent_pos.toString() + ", " + button_states.toString();
    }

    public boolean checkAllEquals(State_individual anotherState) {
        return ((this.agent_pos == null && anotherState.agent_pos == null ||
                (this.agent_pos != null && anotherState.agent_pos != null &&
                        this.agent_pos.equals(anotherState.agent_pos))) &&
                this.button_states.equals(anotherState.button_states));
    }

}

class QtableObject_individual implements Serializable {

    State_individual state;
    double[] actions;

    public QtableObject_individual(State_individual state) {
        this.state = state;
        this.actions = new double[state.button_states.size() + 1];
    }

    public QtableObject_individual(State_individual state, double[] actions) {
        this.state = state;
        this.actions = actions;
    }

    public QtableObject_individual(QtableObject_individual obj) {
        this.state = obj.state;
        this.actions = obj.actions;
    }

    public QtableObject_individual(QtableObject_individual obj, boolean empty) {
        this.state = obj.state;
        this.actions = new double[obj.state.button_states.size() + 1];
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

class TransitionObject_individual {

    State_individual currentState;
    ArrayList<QtableObject_individual> transitions = new ArrayList<QtableObject_individual>();

    public TransitionObject_individual(State_individual currentState) {
        this.currentState = currentState;
    }

    public void udpateTransitionList(QtableObject_individual nextState, int action) {
//        System.out.println(nextState + "  aa  " + action);
        boolean exists = false;
        for (QtableObject_individual state : transitions) {
            if (state.state.checkAllEquals(nextState.state)) {
                state.actions[action] = 1;
                exists = true;
                break;
            }
        }
        if (!exists) {
            transitions.add(new QtableObject_individual(nextState, true));
            transitions.get(transitions.size() - 1).actions[action] = 1;
        }
    }

    @Override
    public String toString() {
//        return "QtableObject {State=" + state.toString() + ", Actions=" + actions.toString() + "}";
        return "TransitionTable {State=(" + currentState.toString() + ") , Transitions(" + transitions.toString() + ")}";
    }

}

class Chart implements Runnable {

    ArrayList<Number> steps_list = new ArrayList<Number>();
    String filename;
    String yValuesName;

    public Chart(ArrayList<Number> steps_list, String filename, String yValuesName) {
        this.steps_list = steps_list;
        this.filename = filename;
        this.yValuesName = yValuesName;
    }

    @Override
    public void run() {
        DefaultCategoryDataset line_chart_dataset = new DefaultCategoryDataset();
        for (int i = 0; i < this.steps_list.size(); i++) {
            System.out.println(this.steps_list.get(i));
            line_chart_dataset.addValue(this.steps_list.get(i), this.yValuesName, i);
        }

        JFreeChart lineChartObject = ChartFactory.createLineChart(
                this.yValuesName + " over episodes", "Episodes",
                this.yValuesName,
                line_chart_dataset, PlotOrientation.VERTICAL,
                true, true, false);

        lineChartObject.getPlot().setBackgroundPaint(Color.WHITE);

        int width = 640;    /* Width of the image */
        int height = 480;   /* Height of the image */
        File lineChart = new File(filename + ".jpeg");
        try {
            ChartUtilities.saveChartAsJPEG(lineChart, lineChartObject, width, height);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("done");
    }
}
