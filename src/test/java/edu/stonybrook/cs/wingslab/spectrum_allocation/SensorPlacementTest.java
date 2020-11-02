package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;
import junit.framework.TestCase;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class SensorPlacementTest extends TestCase {

    // area size
    private int length = 100;
    private int width = 100;
    private int cellSize = 10;

    // ******* sensors' info
    private double std = 1.0;
    private double cost = 0.7;
    private int height = 15;
    private int[] sensorSet = {49};
    private String sensorPath = "../commons/resources/sensors/square100/placement/";

    public void testTerrainBasedPlacement() throws IOException, ClassNotFoundException {
        // ************************* Creating locations probabilities
        double[][] locationsProbability ;
        if (false) // random allocation
            locationsProbability = createLocationsProbability(width, length);
        else { //random-area allocation
            locationsProbability = new double[width][length];
//            createLocationsProbability(locationsProbability, 0, 0, width, length, 1);
        }
//        FileInputStream fis = new FileInputStream(sensorPath + "/locations_probability.dat");
//        ObjectInputStream ois = new ObjectInputStream(fis);
//        locationsProbability = (double[][]) ois.readObject();

        // SPLAT init
        GeographicPoint splat_left_upper_ref = new GeographicPoint(40.800595,
                73.107507);                         // ISLIP lat and lon
        String splatFileName = "pl_map_array.json";            // splat saved file name
        String SPLAT_DIR = "../commons/resources/splat/";

        // READING splat path-loss map
        PropagationModel pm = new Splat(splat_left_upper_ref);
        Splat.readPlDictFromJson(SPLAT_DIR + "pl_map/" + splatFileName);
        Splat.setSdfDir(SPLAT_DIR + "sdf/");

        //terrainBasedPlacement
        SensorPlacement sensorPlacement = new SensorPlacement(pm, new Square(width), null, cellSize,
                height, cost, std);
        sensorPlacement.terrainBasedPlacementPreProcessHimanshu();

        for (int sensorNum : sensorSet) {
            SpectrumSensor[] sensors = sensorPlacement.terrainBasedPlacement(sensorNum);
            //writing sensors as text
            sensorGenerator(sensorPath + "terrain-based/" + sensorNum, sensors);
        }
        //serializing locationsProbability
//        FileOutputStream fos = new FileOutputStream(sensorPath + "/terrain-based/" +
//                "/locations_probability.dat");
//        ObjectOutputStream oos = new ObjectOutputStream(fos);
//        oos.writeObject(locationsProbability);
    }





    public void testProbabilityBasedPlacement() throws IOException, ClassNotFoundException {
        // ************************* Creating locations probabilities
        double[][] locationsProbability ;
        if (false) // random allocation
            locationsProbability = createLocationsProbability(width, length);
        else { //random-area allocation
            locationsProbability = new double[width][length];
//            createLocationsProbability(locationsProbability, 0, 0, width, length, 1);
        }
        FileInputStream fis = new FileInputStream(sensorPath + "/locations_probability.dat");
        ObjectInputStream ois = new ObjectInputStream(fis);
        locationsProbability = (double[][]) ois.readObject();

        //terrainBasedPlacement
        SensorPlacement sensorPlacement = new SensorPlacement(null, new Square(width), locationsProbability, cellSize,
                height, cost, std);

        for (int sensorNum : sensorSet) {
            SpectrumSensor[] sensors = sensorPlacement.probabilityBasedPlacement(sensorNum);
            //writing sensors as text
            sensorGenerator(sensorPath + "probability-based/" + sensorNum, sensors);
        }
    }

    //each cell is random
    private double[][] createLocationsProbability(int width, int length){
        double[][] locationProbability = new double[width][length];
        double sumProb = 0.0;
        for (int x = 0; x < width; x++){
            for (int y = 0; y < length; y++){
                locationProbability[x][y] = ThreadLocalRandom.current().nextDouble();
                sumProb += locationProbability[x][y];
            }
        }

        //updating weight s.t. sum(probs) = 1
        for (int x = 0; x < width; x++)
            for (int y = 0; y < length; y++)
                locationProbability[x][y] /= sumProb;

        return locationProbability;
    }

    //divide the area into 4 disjoint areas recursively and then randomly assigned to each sub-areas
    private void createLocationsProbability(double[][] locationProbability, int originX, int originY,
                                            int width, int length, double sumProb){
        if (width == 1 & length == 1) {
            locationProbability[originX][originY] = sumProb;
            return;
        }
        int halfWidth = width;
        if (width > 1)
            halfWidth /= 2;
        int halfLength = length;
        if (length > 1)
            halfLength /= 2;

        double sum = 0.0;

        double topLeftProb = ThreadLocalRandom.current().nextDouble();
        sum += topLeftProb;

        double bottomLeftProb = -1;
        if (width > halfWidth) {
            bottomLeftProb = ThreadLocalRandom.current().nextDouble();
            sum += bottomLeftProb;
        }

        double topRightProb = -1;
        if (length > halfLength) {
            topRightProb = ThreadLocalRandom.current().nextDouble();
            sum += topRightProb;
        }

        double bottomRightProb = -1;
        if (width > halfWidth & length > halfLength) {
            bottomRightProb = ThreadLocalRandom.current().nextDouble();
            sum += bottomRightProb;
        }

        createLocationsProbability(locationProbability, originX, originY, halfWidth, halfLength,
                topLeftProb * (sumProb / sum));//top-left area

        if (width > halfWidth) // bottom-left corner
            createLocationsProbability(locationProbability, originX + halfWidth, originY,
                    width - halfWidth, halfLength,bottomLeftProb * (sumProb / sum));

        if (length > halfLength) // top-right corner
            createLocationsProbability(locationProbability, originX, originY + halfLength,
                    halfWidth, length - halfLength, topRightProb * (sumProb / sum));

        if (length > halfLength & width > halfWidth)
            createLocationsProbability(locationProbability, originX + halfWidth, originY + halfLength,
                    width - halfWidth, length - halfLength, bottomRightProb * (sumProb / sum));
    }

    private static void sensorGenerator(String sensorFilePath, SpectrumSensor[] sensors){
        Path path = Paths.get(sensorFilePath);// check if the director exists; if not, it try to create it.
        if (!Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            }
            catch (IOException e){
                Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
                logger.warning("Sensor generating operation failed due to I/O error creating directories: " +
                        Arrays.toString(e.getStackTrace()));
                return;
            }
        }

        File file = new File(sensorFilePath + "/sensors.txt");
        try (PrintWriter printLine = new PrintWriter(file)) {
            for (SpectrumSensor sensor : sensors)
                printLine.println(String.format("%1$s, %2$f, %3$f, %4$f", sensor.getRx().getElement().getLocation(),
                        sensor.getRx().getElement().getHeight(), sensor.getStd(), sensor.getCost()));
            printLine.flush();
            printLine.close();
            System.out.printf("File %1s/sensors.txt was successfully created.%n", sensorFilePath);
        } catch (FileNotFoundException e) {
            Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
            logger.warning("Sensor generating operation failed due to I/O error creating the file: " +
                    Arrays.toString(e.getStackTrace()));
        }
    }
}