package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;


/**@author Mohammad Ghaderibaneh <mghaderibane@cs.stonybrook.edu>
 * Application class for spectrum allocation. This class implements Runnable and should be instantiated as a Threat.
 * This class should be initialized with some parameters and then, some text files, containing sample, would be created
 * based on the given parameters. Files would be accessible from results directory.
 * You can change the directory using SpectrumAllocationApp.setDataDir method.
 * 1. PU file: contains PUs and SUs information along with if a particular SU request is accepted or not.
 * 2. Sensor file: contains Sensors and SUs information along with if a particular SU request is accepted or not.
 * 3. Power file: contains requesting sesnor information and the power it is allocated to.*/
public class CSSpectrumAllocationApp implements Runnable{
    enum PUType{
        STATIC,             // pu parameters do not change in this case
        DYNAMIC             // pu location and power change randomly
    }
    private static String DATA_DIR = "resources/data/";
    //directory where the results should be written
    private final int sampleCount;
    // number of samples to be created. in case of SPLAT!, it might be less due to exceptions
    private static int threadNum = 0;
    private final int threadId;
    // will be used when there are multiple threads to write some useful information if a hash dictionary is given
    private final String fileAppendix;
    // will be used to save results; user might need it to merge multiple thread outputs
    private static ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict;
    // some statistics will be written in this dictionary
    private final PropagationModel propagationModel;
    // propagation model that will be used
    private final PU[] pus;
    // array of PUs. they do not change over samples(their location and power might change)
    private final SpectrumSensor[] sss;
    // array of sensor that will be used
    private final Shape shape;
    // shape of the field
    private final int cellSize;
    // square cell size
    private final int minSuNum;
    // minimum number of random SUs user wants to create
    private final int maxSuNum;
    // maximum number of random SUs user wants to create
    private final double minSuPower;
    // minimum power value(dB) an SU can get
    private final double maxSuPower;
    // minimum power value(dB) an SU can get
    private final double suHeight;
    // height of SU the class creates
    private final int minPuNum;
    // number of minimum active PUs for each sample
    private final int maxPuNum;
    // number of maximum active PUs for each sample
    private final double minPuPower;
    // minimum power value(dB) an PU can get
    private final double maxPuPower;
    // minimum power value(dB) an PU can get
    private final double noiseFloor;
    // noise floor level (dB)
    private final PUType puType;
    // progress bar length
    private final static int progressBarLength = 50;
    //number of SSs to be selcted for CSSpectrumManager interpolation
    private final int numSssSelected;
    //number of PUs to be selected
    private final int numPusSelected;
    // interpolation type
    private final CSSpectrumManager.INTERPOLATION interpolationType;
    // alpha for interpolation
    private final double alpha;


    /**
     * SpectrumAllocationApp constructor. Support interpolation.
     * @param sampleCount number of sample to be generated
     * @param fileAppendix an appendix that will be added to results file if provided
     * @param resultDict a hashMap that will be used to write statistics
     * @param propagationModel propagation model
     * @param pus array of PUs
     * @param sss array of SpectrumSensors
     * @param shape shape of the target region
     * @param cellSize size of square cells
     * @param minSuNum minimum number of SUs for each sample
     * @param maxSuNum maximum number of SUs for each sample
     * @param minSuPower minimum power value of SUs for each sample
     * @param maxSuPower maximum power value of SUs for each sample
     * @param suHeight height of SUs
     * @param minPuNum minimum number of PUs for each sample
     * @param maxPuNum maximum number of PUs for each sample
     * @param minPuPower minimum power value of SUs for each sample
     * @param maxPuPower maximum power value of SUs for each sample
     * @param puType if PUs are static or dynamic
     * @param interpolationType interpolation type
     * @param noiseFloor noise floor
     * @param alpha internal parameter for interpolation equivalent to propagation coefficient
     * @param numPusSelected number of PUs selected for splitting sensor's received power
     * @param numSssSelected number of SSs selected to do interpolation for PUR's path-loss*/
    public CSSpectrumAllocationApp(int sampleCount, String fileAppendix,
                                   ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict,
                                   PropagationModel propagationModel, PU[] pus, SpectrumSensor[] sss, Shape shape,
                                   int cellSize, int minSuNum, int maxSuNum, double minSuPower, double maxSuPower,
                                   double suHeight, int minPuNum, int maxPuNum, double minPuPower, double maxPuPower,
                                   PUType puType, CSSpectrumManager.INTERPOLATION interpolationType,
                                   int numPusSelected, int numSssSelected, double alpha, double noiseFloor){
        super();
        this.sampleCount = sampleCount;
        this.threadId = CSSpectrumAllocationApp.threadNum++;
        this.fileAppendix = fileAppendix;
        CSSpectrumAllocationApp.resultDict = resultDict;
        this.propagationModel = propagationModel;
        this.pus = pus;
        this.sss = sss;
        this.shape = shape;
        this.cellSize = cellSize;
        this.minSuNum = minSuNum;
        this.maxSuNum = maxSuNum;
        this.minSuPower = minSuPower;
        this.maxSuPower = maxSuPower;
        this.suHeight = suHeight;
        this.minPuNum = minPuNum;
        this.maxPuNum = maxPuNum;
        this.minPuPower = minPuPower;
        this.maxPuPower = maxPuPower;
        this.noiseFloor = noiseFloor;
        this.puType = puType;
        this.interpolationType = interpolationType;
        this.alpha = alpha;
        this.numPusSelected = numPusSelected;
        this.numSssSelected = numSssSelected;

        //creating files and directory(if needed)
        Path dataPath = Paths.get(CSSpectrumAllocationApp.DATA_DIR);
        if (!Files.isDirectory(dataPath)){
            try {
                Files.createDirectories(dataPath);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(this.getClass().getSimpleName() + " can't create proper directory!");
            }
        }
    }
    /**
     * SpectrumAllocationApp constructor
     * @param sampleCount number of sample to be generated
     * @param fileAppendix an appendix that will be added to results file if provided
     * @param resultDict a hashMap that will be used to write statistics
     * @param propagationModel propagation model
     * @param pus array of PUs
     * @param sss array of SpectrumSensors
     * @param shape shape of the target region
     * @param cellSize size of square cells
     * @param minSuNum minimum number of SUs for each sample
     * @param maxSuNum maximum number of SUs for each sample
     * @param minSuPower minimum power value of SUs for each sample
     * @param maxSuPower maximum power value of SUs for each sample
     * @param suHeight height of SUs
     * @param minPuNum minimum number of PUs for each sample
     * @param maxPuNum maximum number of PUs for each sample
     * @param minPuPower minimum power value of SUs for each sample
     * @param maxPuPower maximum power value of SUs for each sample
     * @param puType if PUs are static or dynamic
     * @param noiseFloor noise floor*/
    public CSSpectrumAllocationApp(int sampleCount, String fileAppendix,
                                   ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict,
                                   PropagationModel propagationModel, PU[] pus, SpectrumSensor[] sss, Shape shape,
                                   int cellSize, int minSuNum, int maxSuNum, double minSuPower, double maxSuPower,
                                   double suHeight, int minPuNum, int maxPuNum, double minPuPower, double maxPuPower,
                                   PUType puType, double noiseFloor) {
        this(sampleCount, fileAppendix, resultDict, propagationModel, pus, sss, shape, cellSize, minSuNum,
                maxSuNum, minSuPower,  maxSuPower, suHeight, minPuNum, maxPuNum, minPuPower, maxPuPower, puType,
                null, 0, 0, 0, noiseFloor);
    }


    /**
     * When an object implementing interface {@code Runnable} is used
     * to create a thread, starting the thread causes the object's
     * {@code run} method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method {@code run} is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        double totalDiffPower = 0.0;
        double totalFPDiffPower = 0.0;
        int validSamples = 0, invalidSamples = 0;
        SpectrumManager sm = new SpectrumManager(this.pus, null, this.sss, this.propagationModel,
                this.shape, this.cellSize, this.noiseFloor);
        // init spectrum manager with fixed parameters; although pu information may change, the objects do not change

        if(this.puType == PUType.STATIC && this.minSuNum == this.maxSuNum && this.minSuNum == 1)
            sm.computeSUMAXPower(false);
        // in case of STATIC PUs and when there is only one requesting, we just want to compute PUs power
        // only once to speedup
        long beginTime = System.currentTimeMillis();
        for (int sample = 1; sample < this.sampleCount + 1; sample++){
            sm.setSus(createSUs());
            if (puType == PUType.DYNAMIC)
                this.createActivePU(); // create(it's not actual creating) new active PUs
            try{
                sm.computeSUMAXPower(this.puType == PUType.STATIC &&
                        this.minSuNum == this.maxSuNum && this.minSuNum == 1);
            }
            catch (RuntimeException e){
                e.printStackTrace();
                continue;
            }
            if (sm.getSuMaxPower() != Double.NEGATIVE_INFINITY){
                CSSpectrumManager csSm = new CSSpectrumManager(this.pus, this.sss, sm.getSus(),
                        this.numPusSelected, this.numSssSelected, this.interpolationType, this.alpha, this.cellSize);
                if (csSm.getSuMaxPower() == Double.POSITIVE_INFINITY ||
                        csSm.getSuMaxPower() == Double.NEGATIVE_INFINITY || Double.isNaN(csSm.getSuMaxPower()))
                   invalidSamples++;
                else {
                    validSamples++;

                    totalDiffPower += Math.abs(sm.getSuMaxPower() - csSm.getSuMaxPower());
                    if (csSm.getSuMaxPower() > sm.getSuMaxPower())
                        totalFPDiffPower += Math.abs(sm.getSuMaxPower() - csSm.getSuMaxPower());
                }
            }

            System.out.print(progressBar(sample, System.currentTimeMillis() - beginTime));
        }
        System.out.println("");
        HashMap<String, Double> threadInfo = new HashMap<>();
        threadInfo.put("Valid Samples", (double) validSamples);
        threadInfo.put("Invalid Samples", (double) invalidSamples);
        threadInfo.put("Average Difference Power", totalDiffPower/validSamples);
        threadInfo.put("Average FP Difference Power", totalFPDiffPower/validSamples);
        if (this.propagationModel instanceof Splat splat){

            threadInfo.put("Fetch Number", (double) splat.getFetchNum());
            threadInfo.put("Fetch Time", (double) splat.getFetchTime());
            threadInfo.put("Execution Number", (double) splat.getExecNum());
            threadInfo.put("Execution Time", (double) splat.getExecTime());
        }
        CSSpectrumAllocationApp.resultDict.put(this.threadId, threadInfo);

    }

    // in case DYNAMIC pus, new set of PU will be active and random power and location will be generated
    private void createActivePU(){
        ArrayList<Integer> allPusIdx = new ArrayList<>();
        int numPus = ThreadLocalRandom.current().nextInt(minPuNum, maxPuNum + 1);
        Point[] puPoints = this.shape.points(numPus);
        for (int i = 0; i < this.pus.length; i++) {
            this.pus[i].setON(false); // disabling all
            allPusIdx.add(i);
        }
        Collections.shuffle(allPusIdx);
        for (int i = 0; i < numPus; i++) {
            this.pus[allPusIdx.get(i)].setON(true); // enabling one by one
            this.pus[allPusIdx.get(i)].getTx().getElement().setLocation(puPoints[i]);
            this.pus[allPusIdx.get(i)].getTx().setPower(ThreadLocalRandom.current().nextDouble(
                    this.minPuPower, this.maxPuPower + Double.MIN_VALUE));
        }
    }

    // creating random sus
    private SU[] createSUs(){
        int susNum = ThreadLocalRandom.current().nextInt(this.minSuNum, this.maxSuNum + 1);
        Point[] susPoint = this.shape.points(susNum);
        SU[] sus = new SU[susNum];
        for (int i = 0; i < susNum - 1; i++)
            sus[i] = new SU(new TX(new Element(susPoint[i], this.suHeight), Double.NEGATIVE_INFINITY));
        // this one is the target to predict
        sus[susNum - 1] = new SU(new TX(new Element(susPoint[susNum - 1], this.suHeight),
                ThreadLocalRandom.current().nextDouble(this.minSuPower, this.maxSuPower + Double.MIN_VALUE)));
        return sus;
    }

    // method to return Progress Bar based on sample #
    private String progressBar(int sampleNumber, long timeElapsedMilli){
        int progress = (int)((float)sampleNumber/this.sampleCount * progressBarLength);  // number of = to be print
        return "=".repeat(Math.max(0, progress)) +
                " ".repeat(Math.max(0, CSSpectrumAllocationApp.progressBarLength - progress)) +
                "| " + (int)((double)sampleNumber / this.sampleCount * 100) + "% " +
                timeFormat(sampleNumber, timeElapsedMilli) + "\r";
    }

    // based on elapsed time and sample number, a time format info will be returned
    private String timeFormat(int sampleNumber, long timeElapseMilli){
        int sampleRemaining = this.sampleCount - sampleNumber;  // remaining samples
        long timeRemainingMilli = (long)(((double) sampleRemaining / sampleNumber) * timeElapseMilli); // remaining time
        String extraInfo = (double)timeElapseMilli / (1000 * sampleNumber) > 1.0 ?
                String.format("%.2fs/it", (double)timeElapseMilli / (1000 * sampleNumber)) :   // seconds per iteration
                String.format("%.2fit/s", (double)(1000 * sampleNumber) / timeElapseMilli);    // iteration per seconds
        return String.format("(%s / %s), %s", timeFormat(timeElapseMilli),
                timeFormat(timeRemainingMilli), extraInfo);
    }

    // return time format (HH:mm:ss) for a milliseconds input
    private String timeFormat(long millis){
        return String.format("%d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
    }

    // **************************** Setter & Getter ******************************
    public static String getDataDir() { return DATA_DIR; }

    public static void setDataDir(String dataDir) { DATA_DIR = dataDir; }

    public static ConcurrentHashMap<Integer, HashMap<String, Double>> getResultDict() { return resultDict; }

    public static void setResultDict(ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict) {
        CSSpectrumAllocationApp.resultDict = resultDict;
    }
}
