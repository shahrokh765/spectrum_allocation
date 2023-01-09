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
public class SpectrumAllocationApp implements Runnable{
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
    // sensors' list to be interpolated
    private final SpectrumSensor[] interSss;
    // number of sensors to use for interpolation
    private final int numberOfInterpolatedSensor;
    // interpolation type {LOG, LINEAR}
    private final InterpolatedSpectrumSensor.InterpolationType interpolationType;
    // set if synthetic samples for pu case would be created or not
    private final boolean puSyntheticSamples;
    // maximum transmission radius used for creating synthetic samples
    private final double maxTransRadius;
    // a boolean indicating if PU location is selected based on a probability table(supported for Rectangle and Square)
    private final boolean PULocationProbabilityBased;
    // A list of number of sensors in case we want to produce a dataset with variable-length sensors. If it has values,
    // sss will be ignores.
    private final int[] sss_counts;

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
     * @param interSss list of sensors to be interpolated
     * @param numberOfInterpolatedSensor number of known sensors to be selected
     * @param interpolationType interpolation type
     * @param puSyntheticSamples indicates if synthetic samples for pu be generated
     * @param maxTransRadius maximum transmission radius maximum power can get
     * @param noiseFloor noise floor
     * @param PULocationProbabilityBased boolean indicate if PU locations are created randomly or based on a probability map*/
    public SpectrumAllocationApp(int sampleCount, String fileAppendix,
                                 ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict,
                                 PropagationModel propagationModel, PU[] pus, SpectrumSensor[] sss, Shape shape,
                                 int cellSize, int minSuNum, int maxSuNum, double minSuPower, double maxSuPower,
                                 double suHeight, int minPuNum, int maxPuNum, double minPuPower, double maxPuPower,
                                 PUType puType, SpectrumSensor[] interSss, int numberOfInterpolatedSensor,
                                 InterpolatedSpectrumSensor.InterpolationType interpolationType,
                                 boolean puSyntheticSamples, double maxTransRadius, double noiseFloor,
                                 boolean PULocationProbabilityBased, int[] sss_counts){
        super();
        this.sampleCount = sampleCount;
        this.threadId = SpectrumAllocationApp.threadNum++;
        this.fileAppendix = fileAppendix;
        SpectrumAllocationApp.resultDict = resultDict;
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
        this.interSss = interSss;
        this.interpolationType = interpolationType;
        this.numberOfInterpolatedSensor = numberOfInterpolatedSensor;
        this.puSyntheticSamples = puSyntheticSamples;
        this.maxTransRadius = maxTransRadius;
        this.PULocationProbabilityBased = PULocationProbabilityBased;
        this.sss_counts = sss_counts;

        //creating files and directory(if needed)
        Path dataPath = Paths.get(SpectrumAllocationApp.DATA_DIR);
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
     * @param noiseFloor noiseFLoor*/
    public SpectrumAllocationApp(int sampleCount, String fileAppendix,
                                 ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict,
                                 PropagationModel propagationModel, PU[] pus, SpectrumSensor[] sss, Shape shape,
                                 int cellSize, int minSuNum, int maxSuNum, double minSuPower, double maxSuPower,
                                 double suHeight, int minPuNum, int maxPuNum, double minPuPower, double maxPuPower,
                                 PUType puType, double noiseFloor,
                                 boolean PULocationProbabilityBased) {
        this(sampleCount, fileAppendix, resultDict, propagationModel, pus, sss, shape, cellSize, minSuNum,
                maxSuNum, minSuPower,  maxSuPower, suHeight, minPuNum, maxPuNum, minPuPower, maxPuPower, puType,
                null, 0, null, false,
                0.0, noiseFloor, PULocationProbabilityBased, new int[]{});
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
        int acceptedNum = 0;            // number of accepted su request
        String fileNameFormat = String.format("_%1$s_%2$d.txt", fileAppendix != null ? fileAppendix : "", this.threadId);
        // this format would be added to file name
        File puFile = new File(SpectrumAllocationApp.DATA_DIR + "/pu" + fileNameFormat);  // pu file
        File ssFile = new File(SpectrumAllocationApp.DATA_DIR + "/sensor" + fileNameFormat); // sensor
        File maxFile = new File(SpectrumAllocationApp.DATA_DIR + "/max" + fileNameFormat);  // max power values
        File interFile = new File(SpectrumAllocationApp.DATA_DIR + "/interSensor" +
                fileNameFormat);  // interpolation file
        File syntheticFile = new File(SpectrumAllocationApp.DATA_DIR + "/syntheticPu" +
                fileNameFormat);    // synthetic pu file
        File suMaxTotalFile = new File(SpectrumAllocationApp.DATA_DIR + "/suMaxTotal" + fileNameFormat);

        int numberOfTotalSus = 0;
        double susDataRate = 0.0;

        // opening files
        try(PrintWriter puWriter = new PrintWriter(puFile);
            PrintWriter ssWriter = new PrintWriter(ssFile);
            PrintWriter maxWriter = new PrintWriter(maxFile);
            PrintWriter interWriter = (this.interSss == null ? null : new PrintWriter(interFile));
            PrintWriter syntheticWriter = (!this.puSyntheticSamples ? null : new PrintWriter(syntheticFile));
            PrintWriter suTotWriter = new PrintWriter(suMaxTotalFile)){
            SpectrumManager sm = new SpectrumManager(this.pus, null, this.sss, this.propagationModel,
                    this.shape, this.cellSize, this.noiseFloor);

            // init spectrum manager with fixed parameters; although pu information may change, the objects do not change

            if(this.puType == PUType.STATIC && this.minSuNum == this.maxSuNum && this.minSuNum == 1)
                sm.computeSUMAXPower(false);
                // in case of STATIC PUs and when there is only one requesting, we just want to compute PUs power
                // only once to speedup
            long beginTime = System.currentTimeMillis();
            for (int sample = 1; sample < this.sampleCount + 1; sample++){
                if (this.sss_counts.length > 0){
                    // TODO: fix hard-coded parameters
                    SpectrumSensor[] tmp_sss = SpectrumSensor.uniformSensorGenerator(
                            sss_counts[ThreadLocalRandom.current().nextInt(sss_counts.length)], this.shape, 0.732,
                            0, 0);
                    sm = new SpectrumManager(this.pus, null, tmp_sss, this.propagationModel,
                            this.shape, this.cellSize, this.noiseFloor);
                }
                sm.setSus(createSUs());
                if (puType == PUType.DYNAMIC) {
                    this.createActivePU();  // create(it's not actual creating) new active PUs
                }
                try{
                    sm.computeSUMAXPower(this.puType == PUType.STATIC &&
                            this.minSuNum == this.maxSuNum && this.minSuNum == 1);
                    suTotWriter.println(sm.computeSUsTotalMaxPower());
                    if (sm.suRequestAccepted())
                        acceptedNum++;
                }
                catch (RuntimeException e){
                    e.printStackTrace();
                    continue;
                }
                puWriter.println(sm.pusSample());
                ssWriter.println(sm.sssSample());
                maxWriter.println(sm.maxPowerSample());
                // interpolation
                if (this.interSss != null) {
                    InterpolatedSpectrumSensor interpolatedSpectrumSensor =
                            new InterpolatedSpectrumSensor(this.sss, this.interSss, this.interpolationType,
                                    this.numberOfInterpolatedSensor, this.cellSize);
                    interWriter.println(String.format("%s,%d,%s,%s", interpolatedSpectrumSensor, sm.getSus().length,
                            sm.susInformation(), sm.suRequestAccepted() ? "1":"0"));
                }
                // synthetic PU samples
                if (this.puSyntheticSamples){
                    SyntheticPUs syntheticPUs = new SyntheticPUs(this.maxTransRadius, this.pus,
                            sm.getMostRestrictivePuIdx(), this.minPuPower, this.cellSize);
                    if (syntheticPUs.isValid())
                        syntheticWriter.println(String.format("%s%d,%s,%s", syntheticPUs, sm.getSus().length,
                                sm.susInformation(), sm.suRequestAccepted() ? "1":"0"));
                }
                // SUS data rate
                double[] tmpSusDataRate = sm.susDataRate();
                for (double suDataRate: tmpSusDataRate){
                    numberOfTotalSus++;
                    susDataRate += suDataRate;
                }
                System.out.print(progressBar(sample, System.currentTimeMillis() - beginTime));
            }
        }
        catch(FileNotFoundException e){
            e.printStackTrace();
            throw new RuntimeException(this.getClass().getSimpleName() + "Failed opening proper files");
        }
        System.out.println("");
        HashMap<String, Double> threadInfo = new HashMap<>();
        threadInfo.put("Accepted Number", (double) acceptedNum);
        threadInfo.put("Average Data Rate", susDataRate/(numberOfTotalSus * 1e6));
        if (this.propagationModel instanceof Splat splat){

            threadInfo.put("Fetch Number", (double) splat.getFetchNum());
            threadInfo.put("Fetch Time", (double) splat.getFetchTime());
            threadInfo.put("Execution Number", (double) splat.getExecNum());
            threadInfo.put("Execution Time", (double) splat.getExecTime());
        }
        SpectrumAllocationApp.resultDict.put(this.threadId, threadInfo);

    }

    // in case DYNAMIC pus, new set of PU will be active and random power and location will be generated
    private void createActivePU(){
        ArrayList<Integer> allPusIdx = new ArrayList<>();
        int numPus = ThreadLocalRandom.current().nextInt(minPuNum, maxPuNum + 1);
        Point[] puPoints;
        if (!PULocationProbabilityBased)
            puPoints = this.shape.points(numPus);
        else {
            if (this.shape.getClass() != Rectangle.class & this.shape.getClass() != Square.class)
                throw new RuntimeException("Shape not supported for PU probability-based selection");
            Rectangle rectangle = (Rectangle)this.shape;
            puPoints = rectangle.ProbabilityBasedPoints(numPus);
        }
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
        Rectangle rectangle = (Rectangle)this.shape;
        for (int i = 0; i < susNum - 1; i++)
            sus[i] = new SU(new TX(new Element(susPoint[i], this.suHeight), Double.NEGATIVE_INFINITY),
                    new Element(susPoint[i].add(new Point(new PolarPoint(ThreadLocalRandom.current().nextDouble(
                            30),
                            ThreadLocalRandom.current().nextDouble(Math.PI)))), this.suHeight));
        // this one is the target to predict
        sus[susNum - 1] = new SU(new TX(new Element(susPoint[susNum - 1], this.suHeight),
                ThreadLocalRandom.current().nextDouble(this.minSuPower, this.maxSuPower + Double.MIN_VALUE)),
                new Element(susPoint[susNum - 1].add(new Point(new PolarPoint(ThreadLocalRandom.current().nextDouble(
                        30),
                        ThreadLocalRandom.current().nextDouble(Math.PI)))), this.suHeight));
        return sus;
    }

    // method to return Progress Bar based on sample #
    private String progressBar(int sampleNumber, long timeElapsedMilli){
        int progress = (int)((float)sampleNumber/this.sampleCount * progressBarLength);  // number of = to be print
        return "=".repeat(Math.max(0, progress)) +
                " ".repeat(Math.max(0, SpectrumAllocationApp.progressBarLength - progress)) +
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
        SpectrumAllocationApp.resultDict = resultDict;
    }
}
