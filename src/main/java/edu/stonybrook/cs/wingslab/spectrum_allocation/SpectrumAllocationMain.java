package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SpectrumAllocationMain {
    public static void main(String... args) throws InterruptedException, IOException, ClassNotFoundException {
        // **********************************   PATHS   ****************************************
        String TMP_DIR = "resources/tmp/";
        String SPLAT_DIR = "../commons/resources/splat/";
        String SENSOR_PATH = "../commons/resources/sensors/";
        //String DATA_PATH = "resources/data";

        // ********************************** Field Parameters **********************************
        double tx_height = 30;                          // in meter
        double rx_height =  15;                         // in meter
        Shape field_shape = new Square(100);       // Square and Rectangle are supported for now.
                                                        // in meter and originated in (0, 0). 1000 for log, 100 for splat
        int cell_size = 10;                               // in meter

        // ********************************** Propagation Model **********************************
        String propagationModel = "splat";                // 'splat' or 'log'
        double alpha = 3.3;                               // propagation model coeff.  2.0 for 4km, 3 for 1km, 4.9 for 200m.
                                                        // Applicable for log
        boolean noise = true;                           // std in dB.
        double std =  1.0;                              // Applicable for log
        GeographicPoint splat_left_upper_ref = new GeographicPoint(40.800595, 73.107507);      // ISLIP lat and lon
        //GeographicPoint splat_left_upper_ref = new GeographicPoint(40.912004,73.122076); // SBU
        double noise_floor = -120;                       // noise floor
        String splatFileName = "pl_map_array.mcarthur.json";            // splat saved file name
        //SharedDictionary = False  # means pl_map is shared among sub process or not. Applicatble for Splat

        // ********************************** PUs&PURs **********************************
        SpectrumAllocationApp.PUType puType =
                SpectrumAllocationApp.PUType.DYNAMIC;   // DYNAMIC means PU's power and location changes for each sample.
                                                        // STATIC means they do not change.
        int min_pus_number = 10;                        // min number of pus all over the field
        int max_pus_number = 20;                        // max number of pus all over the field;
                                                        // i.e. # of pus is different for each sample.
                                                        // min=max means # of pus doesn't change
        double min_pu_power = -30.0;
        double max_pu_power = 0.0;                      // in dB. PU's power do not change for static PUs case
        int pur_number = 5;                            // number of purs each pu can have 10 for log, 5 for splat
        PUR.InterferenceMethod pur_metric =
                PUR.InterferenceMethod.BETA;            // BETA and THRESHOLD
        double pur_metric_value = 0.05;                  // beta: 0.05 for splat and 0.1 for log, threshold(power in dB)
        double min_pur_dist = 1.0;                      // * cell_size, min_distance from each pur to its pu
        double max_pur_dist = 2.0;                      // * cell_size, max_distance from each pur to its pu
        boolean PU_LOCATION_BASED_PROBABILITY = false;
        String PROBABILITY_TABLE_PATH = "../commons/resources/sensors/square100/placement/locations_probability.dat";               

        // ********************************** SUs **********************************
        int min_sus_number = 1;
        int max_sus_number = 5;                         // min(max) number of sus; i.e. # of sus is different for each sample.
        double min_su_power = min_pu_power - 5;         // used for binary case
        double max_su_power = max_pu_power + 55;        // used for binary case

        // ********************************** SSs **********************************
        int number_sensors = 49;
        final int[] variable_length_ss = new int[]{};  // This has a priority over number_sensors
        boolean PLACEMENT = false;       // indicate if we want select sensors uniformly(false) or through placement algo

        // ********************************** Interpolated Sensors ********************
        boolean IS_INTERPOLATED = false;
        int numberInterpolatedSensors = 625;
        int numberOfSensorsInterpolated = 4;
        InterpolatedSpectrumSensor.InterpolationType interpolationType =
                InterpolatedSpectrumSensor.InterpolationType.LOG;

        // ********************************** Synthetic PUs *****************************
        boolean IS_SYNTHETIC = false;
        double maxTransRadius = 0.0;
        if (propagationModel.equals("log"))
            maxTransRadius = Math.pow(10, (max_pu_power - noise_floor)/(10 * alpha));
        else{
            // TODO find it systematically
            maxTransRadius = 400;
        }

        // ********************************** General **********************************
        // MAX_POWER = True   # make it true if you want to achieve the highest power su can have without interference.
        // calculation for conservative model would also be done
        int number_of_process = 8;                      // number of process
        //INTERPOLATION, CONSERVATIVE = False, False
        int n_samples = 25000;                            // number of samples

        long beginTime = System.currentTimeMillis();
        String sensorPath;
        if (!PLACEMENT)
            sensorPath = String.format("%s%s/%d/sensors.txt", SENSOR_PATH, field_shape.toString(),
                    number_sensors);
        else
            sensorPath = String.format("%s%s/placement/terrain-based/himanshu/%d/sensors.txt",
                    SENSOR_PATH, field_shape.toString(),
                    number_sensors);

        String interSensorPath;
        SpectrumSensor[] interSss = null;
        if (IS_INTERPOLATED) {
            interSensorPath = String.format("%s%s/%d/sensors.txt", SENSOR_PATH, field_shape.toString(),
                    numberInterpolatedSensors);
            try {
                interSss = SpectrumSensor.SensorReader(interSensorPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // create proper propagation model
        PropagationModel pm = null;
        if (propagationModel.equals("log"))
            if (noise)
                pm = new LogDistancePM(alpha, std);
            else
                pm = new LogDistancePM(alpha);
        else if (propagationModel.equals("splat")) {
            pm = new Splat(splat_left_upper_ref);
            Splat.readPlDictFromJson(SPLAT_DIR + "pl_map/" + splatFileName);
            Splat.setSdfDir(SPLAT_DIR + "sdf/");
        }

        // creating sensors
        SpectrumSensor[] sss = null;
        try {
            sss = SpectrumSensor.SensorReader(sensorPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // creating pus
        double[][] locationProbability = null;
        if (PU_LOCATION_BASED_PROBABILITY && propagationModel.toLowerCase().equals("splat")){
            FileInputStream fis = new FileInputStream(PROBABILITY_TABLE_PATH);
            ObjectInputStream ois = new ObjectInputStream(fis);
            locationProbability = (double[][]) ois.readObject();
            Rectangle.calculateWeightedPoints(locationProbability, 100, 100);
        }
        PU[] pus = createPUs(max_pus_number, field_shape, tx_height, min_pu_power, max_pu_power,
                pur_metric, pur_metric_value, pur_number,
                min_pur_dist, max_pur_dist, rx_height);

        // TODO implement std calculation
        if (false) {
            CalculateSTD calculateSTD = new CalculateSTD(pus, pm, field_shape, cell_size, noise_floor);
            System.out.println(calculateSTD.getStd());
        }

        // ****************************** creating threads ************************
        ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict = new ConcurrentHashMap<>();
        int[] threadSampleNum = new int[number_of_process];
        for (int i = 0; i < number_of_process; i++)
            threadSampleNum[i] = n_samples / number_of_process;         // equally distributed among threads
        threadSampleNum[0] += n_samples - (n_samples / number_of_process) * number_of_process;
                                                                        // remaining will be assigned to the first one
        int fileAppendix =
                ThreadLocalRandom.current().nextInt(100000);      // a random value will be used to distinguish
                                                                        // created file by different run
        Thread[] threads = new Thread[number_of_process];               // threads
        for (int i = 0; i < number_of_process; i++){
            // creating new thread
            PU[] threadCopyPUs = new PU[pus.length];
            for (int puId = 0; puId < pus.length; puId++)
                threadCopyPUs[puId] = new PU(pus[puId]);

            SpectrumSensor[] threadCopySss = new SpectrumSensor[sss.length];
            for (int ssId = 0; ssId < sss.length; ssId++)
                threadCopySss[ssId] = new SpectrumSensor(sss[ssId]);

            PropagationModel threadPM;
            if (pm instanceof LogDistancePM logDistancePM)
                threadPM = new LogDistancePM(logDistancePM);
            else if (pm instanceof Splat splat)
                threadPM = new Splat(splat);
            else
                throw new IllegalArgumentException("Constructor is not valid.");

            Shape threadShape;
            if (field_shape instanceof Rectangle rectangle)
                threadShape = new Rectangle(rectangle);
            else if (field_shape instanceof Square square)
                threadShape = new Square(square);
            else
                throw new IllegalArgumentException("Shape is not valid.");
            if (!IS_INTERPOLATED)
                threads[i] = new Thread(new SpectrumAllocationApp(threadSampleNum[i], Integer.toString(fileAppendix),
                        resultDict, threadPM, threadCopyPUs, threadCopySss, threadShape, cell_size,
                        min_sus_number, max_sus_number, min_su_power, max_su_power, tx_height,
                        min_pus_number, max_pus_number, min_pu_power, max_pu_power, puType,
                        null, 0, null, IS_SYNTHETIC,
                        maxTransRadius, noise_floor, PU_LOCATION_BASED_PROBABILITY, variable_length_ss));
            else{
                SpectrumSensor[] threadCopyInterSss = new SpectrumSensor[interSss.length];
                for (int interSsId = 0; interSsId < interSss.length; interSsId++)
                    threadCopyInterSss[interSsId] = new SpectrumSensor(interSss[interSsId]);

                threads[i] = new Thread(new SpectrumAllocationApp(threadSampleNum[i], Integer.toString(fileAppendix),
                        resultDict, threadPM, threadCopyPUs, threadCopySss, threadShape, cell_size,
                        min_sus_number, max_sus_number, min_su_power, max_su_power, tx_height,
                        min_pus_number, max_pus_number, min_pu_power, max_pu_power, puType,
                        threadCopyInterSss,
                        numberOfSensorsInterpolated, interpolationType,
                        IS_SYNTHETIC, maxTransRadius, noise_floor, PU_LOCATION_BASED_PROBABILITY, variable_length_ss));
            }
            threads[i].start();
        }

        // waiting for all the threads to finish their jobs
        for (Thread thread : threads)
            thread.join();

        // merging result file generated by threads into one
        String date = new SimpleDateFormat("_yyyy_MM_dd_HH_mm").format(new Date());
        String output_format = n_samples + "_" +
                (min_pus_number != max_pus_number ?
                        "min" + min_pus_number + "_max" + max_pus_number :
                        max_pus_number) + "PUs" + "_" +
                (min_sus_number != max_sus_number ?
                        "min" + min_sus_number + "_max" + max_sus_number :
                        max_sus_number) + "SUs" + "_" +
                field_shape + "grid_" + propagationModel +
                (noise && propagationModel.contains("log") ?
                        "_noisy_std" + std :
                        "")
                + date + ".txt";
        mergeFiles(SpectrumAllocationApp.getDataDir(), "pu_" + fileAppendix, // merging pu related files
                SpectrumAllocationApp.getDataDir(), "dynamic_pus_using_pus_" + output_format);
        System.out.println("File " + "dynamic_pus_using_pus_" + output_format + " saved at: " +
                SpectrumAllocationApp.getDataDir());

        mergeFiles(SpectrumAllocationApp.getDataDir(), "max_" + fileAppendix, // merging max information
                SpectrumAllocationApp.getDataDir(), "dynamic_pus_max_power_" + output_format);
        System.out.println("File " + "dynamic_pus_max_power_" + output_format + " saved at: " +
                SpectrumAllocationApp.getDataDir());

        if (variable_length_ss.length == 0) {
            mergeFiles(SpectrumAllocationApp.getDataDir(), "sensor_" + fileAppendix,  // merging sensor related files
                    SpectrumAllocationApp.getDataDir(), "dynamic_pus_" + number_sensors + "sensor_" + output_format);
            System.out.println("File " + "dynamic_pus_" + number_sensors + "sensor_" + output_format + " saved at: " +
                    SpectrumAllocationApp.getDataDir());
        }else {
            mergeFiles(SpectrumAllocationApp.getDataDir(), "sensor_" + fileAppendix,  // merging sensor related files
                    SpectrumAllocationApp.getDataDir(), "dynamic_pus_variable_sensors_" + output_format);
            System.out.println("File " + "dynamic_pus_variable_sensors_" + output_format + " saved at: " +
                    SpectrumAllocationApp.getDataDir());
        }
        if (IS_INTERPOLATED) {
            mergeFiles(SpectrumAllocationApp.getDataDir(), "interSensor_" + fileAppendix,  // merging sensor related files
                    SpectrumAllocationApp.getDataDir(), "dynamic_pus_" +
                            numberInterpolatedSensors + "InterpolatedSensor_" + output_format);
            System.out.println("File " + "dynamic_pus_" + numberInterpolatedSensors + "InterpolatedSensor_" +
                    output_format + " saved at: " + SpectrumAllocationApp.getDataDir());
        }
        if (IS_SYNTHETIC){
            mergeFiles(SpectrumAllocationApp.getDataDir(), "syntheticPu_" + fileAppendix,  // merging sensor related files
                    SpectrumAllocationApp.getDataDir(), "dynamic_pus_synthetic"  + output_format);
            System.out.println("File " + "dynamic_pus_synthetic_" + output_format
                    + " saved at: " + SpectrumAllocationApp.getDataDir());
        }

        mergeFiles(SpectrumAllocationApp.getDataDir(), "suMaxTotal_" + fileAppendix, // merging max information
                SpectrumAllocationApp.getDataDir(), "dynamic_pus_maximum_total_sus" + output_format);
        System.out.println("File " + "dynamic_pus_maximum_total_sus" + output_format + " saved at: " +
                SpectrumAllocationApp.getDataDir());


        // displaying statistics
        int numSampleAccepted = 0;
        for (HashMap<String, Double> threadInfo : resultDict.values()) {
            if (threadInfo.containsKey("Accepted Number"))
                numSampleAccepted += threadInfo.get("Accepted Number").intValue();
        }
        System.out.println("Number of accepted samples:" + numSampleAccepted);
        // more for splat
        if (propagationModel.contains("splat")){
            long fetchNum = 0;           // number of using hash map
            long execNum = 0;            // number of executing splat command line
            long fetchTime = 0;          // duration(milliseconds) of fetching
            long execTime = 0;           // duration(milliseconds) of executing
            for (HashMap<String, Double> threadInfo : resultDict.values()) {
                if (threadInfo.containsKey("Fetch Number"))
                    fetchNum += threadInfo.get("Fetch Number").longValue();
                if (threadInfo.containsKey("Execution Number"))
                    execNum += threadInfo.get("Execution Number").longValue();
                if (threadInfo.containsKey("Fetch Time"))
                    fetchTime += threadInfo.get("Fetch Time").longValue();
                if (threadInfo.containsKey("Execution Time"))
                    execTime += threadInfo.get("Execution Time").longValue();
            }
            System.out.printf("Fetching: %d times (%fms per each)\n" +
                            "Execution Time: %d times (%.2fms per each)%n",
                    fetchNum, (double) fetchTime / fetchNum, execNum, (double) execTime / execNum);

            // saving new pl map
            if (execNum > 10)
                Splat.writePlDictToJson(SPLAT_DIR + "pl_map/" + splatFileName + ".new");
        }
        // calculating data rates
        double totalDataRate = 0.0;
        int numberOfThreads = 0;
        for (HashMap<String, Double> threadInfo : resultDict.values()){
            if (threadInfo.containsKey("Average Data Rate")){
                totalDataRate += threadInfo.get("Average Data Rate");
                numberOfThreads += 1;
            }
        }
        System.out.printf("Average Data Rate is: %.2f Mbps\n", totalDataRate/numberOfThreads);

        long duration = System.currentTimeMillis() - beginTime;
        System.out.println(String.format("Duration = %d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(duration),
                TimeUnit.MILLISECONDS.toMinutes(duration) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(duration) % TimeUnit.MINUTES.toSeconds(1)));
    }

    // method to create PUs
    private static PU[] createPUs(int puNum, Shape shape, double puHeight, double minPuPower, double maxPuPower,
                                  PUR.InterferenceMethod purMetric, double purMetricValue,
                                  int purNum, double purMinDist, double purMaxDist,
                                  double purHeight){
        PU[] pus = new PU[puNum];
        int puCnt = 0;
        for (Point point : shape.points(puNum))
            pus[puCnt++] = new PU(new TX(new Element(point, puHeight),
                    ThreadLocalRandom.current().nextDouble(minPuPower, maxPuPower + Double.MIN_VALUE)),
                    purNum, purMetric, purMetricValue, purMinDist, purMaxDist, purHeight);
        return pus;
    }

    // merging results from multiple threads
    private static void mergeFiles(String srcPath, String pattern, String destPath, String fileName){
        Path path = Paths.get(destPath);// check if the director exists; if not, it try to create it.
        if (!Files.isDirectory(path)) {
            try {
                Files.createDirectories(path);
            }
            catch (IOException e){
                Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
                logger.warning("Merging file was unsuccessful. Creating dest directory faile." +
                        Arrays.toString(e.getStackTrace()));
                return;
            }
        }
        // read all the files name
        File dir = new File(srcPath);
        File[] files = dir.listFiles((d, name) -> name.startsWith(pattern));
        if (files == null || files.length == 0){
            Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
            logger.warning("Merging outputs: No such files was found");
            return;
        }
        Arrays.sort(files); // sorting files to avid misplacement
        // merging files
        File outputFile = new File(destPath + fileName);
        try(PrintWriter printWriter = new PrintWriter(outputFile)){
            for (File file : files){
                Scanner fileScanner = new Scanner(file);
                while (fileScanner.hasNextLine())
                    printWriter.println(fileScanner.nextLine());
                fileScanner.close();
                file.delete();
            }
        } catch (FileNotFoundException e) {
            Logger logger = Logger.getLogger(SpectrumSensor.class.getName());
            logger.warning("Merging output failed due to I/O error creating the file: " +
                    Arrays.toString(e.getStackTrace()));
        }
    }

}
