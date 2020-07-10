package agents;

import helperclasses.datastructures.Tuple;
import helperclasses.datastructures.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;

public class Bruno_2agents_MDP_main {


    @BeforeAll
    static void start() {
        // Uncomment this to make the game's graphic visible:
//        TestSettings.USE_GRAPHICS = true;
//        String labRecruitsExeRootDir = System.getProperty("user.dir");
//        labRecruitsTestServer = TestSettings.start_LabRecruitsTestServer(labRecruitsExeRootDir);
    }

    @AfterAll
    static void close() {
    }

    @Test
    public void run() throws Exception {

        Tuple<ArrayList<Vec3>, ArrayList<Vec3>> positions = null;

//        String scenario = "scenario13";
//        String target1 = "button2";
//        String target2 = "button3";

        String scenario = "scenario14";
        String target1 = "button1";
        String target2 = "button3";

//        centralizedTraining(scenario, target1, target2, true);
//        positions = centralizedTesting(scenario, target1, target2, true);

//        individualTraining(scenario, target1, target2, true);
        positions = individualTesting(scenario, target1, target2);


//        saveToTXT("C:/Users/bruno/Desktop/Ambiente de Trabalho/" + scenario + "_individual_agent0.txt", positions.object1);
//        saveToTXT("C:/Users/bruno/Desktop/Ambiente de Trabalho/" + scenario + "_individual_agent1.txt", positions.object2);

    }

    public void centralizedTraining(String scenario, String target1, String target2) throws Exception {
        Bruno_2agents_centralized_MDP_train.start();
        Bruno_2agents_centralized_MDP_train centralized_train_new = new Bruno_2agents_centralized_MDP_train();
        centralized_train_new.create_policy_train(scenario, target1, target2);
        Bruno_2agents_centralized_MDP_train.close();

    }

    public Tuple<ArrayList<Vec3>, ArrayList<Vec3>> centralizedTesting(String scenario, String target1, String target2) throws Exception {
        Tuple<ArrayList<Vec3>, ArrayList<Vec3>> Positions = null;
        Bruno_2agents_centralized_MDP_test.start();
        Bruno_2agents_centralized_MDP_test centralized_test = new Bruno_2agents_centralized_MDP_test();
        Positions = centralized_test.run(scenario, target1, target2);
        Bruno_2agents_centralized_MDP_test.close();

        return Positions;
    }

    public void individualTraining(String scenario, String target1, String target2) throws Exception {
        Bruno_2agents_individual_MDP_train.start();
        Bruno_2agents_individual_MDP_train individual_train_new = new Bruno_2agents_individual_MDP_train();
        individual_train_new.create_policy_train(scenario, target1, target2);
        Bruno_2agents_individual_MDP_train.close();
    }

    public Tuple<ArrayList<Vec3>, ArrayList<Vec3>> individualTesting(String scenario, String target1, String target2) throws Exception {
        Tuple<ArrayList<Vec3>, ArrayList<Vec3>> Positions = null;

        Bruno_2agents_individual_MDP_test.start();
        Bruno_2agents_individual_MDP_test individual_test = new Bruno_2agents_individual_MDP_test();
        Positions = individual_test.run(scenario, target1, target2);
        Bruno_2agents_individual_MDP_test.close();


        return Positions;
    }

    public void saveToTXT(String filename, ArrayList<Vec3> list) throws IOException {
        FileWriter write = new FileWriter(filename, false);
        PrintWriter print_line = new PrintWriter(write);

        for (Vec3 pos : list)
            print_line.println(pos.toString());

        print_line.close();

    }

}
