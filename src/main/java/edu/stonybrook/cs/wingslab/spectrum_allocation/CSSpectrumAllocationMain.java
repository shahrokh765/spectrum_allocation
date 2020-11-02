package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
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

public class CSSpectrumAllocationMain {
    public static void main(String... args) throws InterruptedException {
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
        double alpha = 3;                               // propagation model coeff.  2.0 for 4km, 3 for 1km, 4.9 for 200m.
                                                        // Applicable for log
        boolean noise = true;                           // std in dB.
        double std =  1.0;                              // Applicable for log
        GeographicPoint splat_left_upper_ref = new GeographicPoint(40.800595,73.107507);  // ISLIP lat and lon
        double noise_floor = -90;                       // noise floor
        String splatFileName = "pl_map_array.json";            // splat saved file name
        //SharedDictionary = False  # means pl_map is shared among sub process or not. Applicatble for Splat

        // ********************************** PUs&PURs **********************************
        CSSpectrumAllocationApp.PUType puType =
                CSSpectrumAllocationApp.PUType.DYNAMIC;   // DYNAMIC means PU's power and location changes for each sample.
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
        double pur_metric_value = 0.05;                  // beta: 0.05 for splat and 1 for log, threshold(power in dB)
        double min_pur_dist = 1.0;                      // * cell_size, min_distance from each pur to its pu
        double max_pur_dist = 2.0;                      // * cell_size, max_distance from each pur to its pu

        // ********************************** SUs **********************************
        int min_sus_number = 1;
        int max_sus_number = 1;                         // min(max) number of sus; i.e. # of sus is different for each sample.
        double min_su_power = min_pu_power - 5;         // used for binary case
        double max_su_power = max_pu_power + 55;        // used for binary case

        // ********************************** SSs **********************************
        int number_sensors = 3600;

        // ********************************** CS Spectrum Manager *********************
        double csAlpha = 3.3;       // best values for splat [3.2-3.5]
        int numPusSelected = 35;
        int numSssSelected = 15;
        CSSpectrumManager.INTERPOLATION interpolationType = CSSpectrumManager.INTERPOLATION.IDW;

        // ********************************** General **********************************
        // MAX_POWER = True   # make it true if you want to achieve the highest power su can have without interference.
        // calculation for conservative model would also be done
        int number_of_process = 5;                      // number of process
        //INTERPOLATION, CONSERVATIVE = False, False
        int n_samples = 5000;                            // number of samples

        long beginTime = System.currentTimeMillis();
        String sensorPath = String.format("%s%s/%d/sensors.txt", SENSOR_PATH, field_shape.toString(),
                number_sensors);

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
        PU[] pus = createPUs(max_pus_number, field_shape, tx_height, min_pu_power, max_pu_power,
                pur_metric, pur_metric_value, pur_number,
                min_pur_dist, max_pur_dist, rx_height);

        // TODO implement std calculation

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
            threads[i] = new Thread(new CSSpectrumAllocationApp(threadSampleNum[i], Integer.toString(fileAppendix),
                    resultDict, threadPM, threadCopyPUs, threadCopySss, threadShape, cell_size,
                    min_sus_number, max_sus_number, min_su_power, max_su_power, tx_height,
                    min_pus_number, max_pus_number, min_pu_power, max_pu_power, puType,
                    interpolationType, numPusSelected, numSssSelected, csAlpha, noise_floor));
            threads[i].start();
        }

        // waiting for all the threads to finish their jobs
        for (Thread thread : threads)
            thread.join();

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
            System.out.println(String.format("Fetching: %,d times (%fms per each)\n" +
                    "Execution Time: %,d times (%.2fms per each)",
                    fetchNum, (double) fetchTime / fetchNum, execNum, (double) execTime / execNum));

            // saving new pl map
            if (execNum > 10)
                Splat.writePlDictToJson(SPLAT_DIR + "pl_map/" + splatFileName + ".new");
        }

        // displaying results
        int validSamples = 0, invalidSamples = 0;
        double totalAverageDiffPower = 0.0;
        int totalPowerCnt = 0;
        double totalAverageFPDiffPower = 0.0;
        int totalFpPowerCnt = 0;
        for (HashMap<String, Double> threadInfo : resultDict.values()) {
            if (threadInfo.containsKey("Valid Samples"))
                validSamples += threadInfo.get("Valid Samples").intValue();
            if (threadInfo.containsKey("Invalid Samples"))
                invalidSamples += threadInfo.get("Invalid Samples").intValue();
            if (threadInfo.containsKey("Average Difference Power")) {
                totalAverageDiffPower += threadInfo.get("Average Difference Power");
                totalPowerCnt++;
            }
            if (threadInfo.containsKey("Average FP Difference Power")) {
                totalAverageFPDiffPower += threadInfo.get("Average FP Difference Power");
                totalFpPowerCnt++;
            }
        }
        System.out.println("");
        System.out.println(String.format("""
                        Number of Samples (invalid) = %d (%d)
                        Total Average Power Diff. = %.2f\s
                        Total FP Average Power Diff. = %.2f""",
                validSamples, invalidSamples, totalAverageDiffPower/totalPowerCnt,
                totalAverageFPDiffPower/totalFpPowerCnt));
        long duration = System.currentTimeMillis() - beginTime;
        System.out.println(String.format("\nDuration = %d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(duration),
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
