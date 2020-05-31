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

enum PUType{
    STATIC,             // pu parameters do not change in this case
    DYNAMIC             // pu location and power change randomly
}
/**@author Mohammad Ghaderibaneh <mghaderibane@cs.stonybrook.edu>
 * Application class for spectrum allocation.
 * This class should be initialized with some parameters and then, some text files, containing sample, would be created
 * based on the given parameters. Files would be accessible from results directory.
 * 1. PU file: contains PUs and SUs information along with if a particular SU request is accepted or not.
 * 2. Sensor file: contains Sensors and SUs information along with if a particular SU request is accepted or not.
 * 3. Power file: contains requesting sesnor information and the power it is allocated to.*/
public class SpectrumAllocationApp implements Runnable{
    private static String DATA_DIR = "resources/data";
    //directory where the results should be written
    private final int sampleCount;
    // number of samples to be created. in case of SPLAT!, it might be less due to exceptions
    private static int threadNum = 0;
    private final int threadId;
    // will be used when there are multiple threads to write some useful information if a hash dictionary is given
    private final String fileAppendix;
    // will be used to save results; user might need it to merge multiple thread outputs
    private ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict;
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

    public SpectrumAllocationApp(int sampleCount, String fileAppendix,
                                 ConcurrentHashMap<Integer, HashMap<String, Double>> resultDict,
                                 PropagationModel propagationModel, PU[] pus, SpectrumSensor[] sss, Shape shape,
                                 int cellSize, int minSuNum, int maxSuNum, double minSuPower, double maxSuPower,
                                 double suHeight, int minPuNum, int maxPuNum, double minPuPower, double maxPuPower,
                                 double noiseFloor, PUType puType) {
        super();
        this.sampleCount = sampleCount;
        this.threadId = SpectrumAllocationApp.threadNum++;
        this.fileAppendix = fileAppendix;
        this.resultDict = resultDict;
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
        String fileNameFormat = String.format("_%1$s_%2$d.txt", fileAppendix, this.threadId);
        // this format would be added to file anme
        File puFile = new File(SpectrumAllocationApp.DATA_DIR + "/pu" + fileNameFormat);  // pu file
        File ssFile = new File(SpectrumAllocationApp.DATA_DIR + "/sensor" + fileNameFormat); // sensor
        File maxFile = new File(SpectrumAllocationApp.DATA_DIR + "/max" + fileNameFormat);  // max power values

        // opening files
        try(PrintWriter puWriter = new PrintWriter(puFile);
                PrintWriter ssWriter = new PrintWriter(ssFile);
                PrintWriter maxWriter = new PrintWriter(maxFile)){
            SpectrumManager sm = new SpectrumManager(this.pus, null, this.sss, this.propagationModel,
                    this.shape, this.cellSize);
            // init spectrum manager with fixed parameters; although pu information may change, the objects do not change

            if(this.puType == PUType.STATIC && this.minSuNum == this.maxSuNum && this.minSuNum == 1)
                sm.computeSUMAXPower(false);
            // in case of STATIC PUs and when there is only one requesting, we just want to compute PUs power
            // only once to speedup
            for (int sample = 1; sample < this.sampleCount + 1; sample++){
                sm.setSus(createSUs());
                int pusNum;  // used fpr serialization
                if (puType == PUType.DYNAMIC)
                    pusNum = this.createActivePU(); // create(it's not real creating) new active PUs
                else
                    pusNum = this.pus.length;
                try{
                    sm.computeSUMAXPower(this.puType == PUType.STATIC &&
                            this.minSuNum == this.maxSuNum && this.minSuNum == 1);
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
            }
        }
        catch(FileNotFoundException e){
            e.printStackTrace();
            throw new RuntimeException(this.getClass().getSimpleName() + "Failed opening proper files");
        }
        HashMap<String, Double> threadInfo = new HashMap<>();
        threadInfo.put("Accepted Number", (double) acceptedNum);
        if (this.propagationModel instanceof Splat splat){

            threadInfo.put("Fetch Number", (double) splat.getFetchNum());
            threadInfo.put("Fetch Time", splat.getFetchTime());
            threadInfo.put("Execution Number", (double) splat.getExecNum());
            threadInfo.put("Execution Time", splat.getExecTime());
        }
        this.resultDict.put(this.threadId, threadInfo);

    }

    private int createActivePU(){
        ArrayList<Integer> allPusIdx = new ArrayList<>();
        int numPus = ThreadLocalRandom.current().nextInt(minPuNum, maxPuNum + 1);
        Point[] puPoints = this.shape.points(numPus);
        int[] activePusIdx = new int[numPus];
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
        return numPus;
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
        StringBuilder out = new StringBuilder("");
        int progress = (int)((float)sampleNumber/this.sampleCount * progressBarLength);  // number of = to be print
        out.append("=".repeat(Math.max(0, progress)));
        out.append(" ".repeat(Math.max(0, SpectrumAllocationApp.progressBarLength - progress)));
        out.append(timeFormat(sampleNumber, timeElapsedMilli)).append("\r");
        return out.toString();
    }

    // based on elapsed time and sample number, a time format info will be returned
    private String timeFormat(int sampleNumber, long timeElapseMilli){
        int sampleRemaining = this.sampleCount - sampleNumber;  // remaining samples
        long timeRemainingMilli = (long)(((double) sampleRemaining / sampleNumber) * timeElapseMilli); // remaining time
        String extraInfo = (double)timeElapseMilli / (1000 * sampleNumber) > 1.0 ?
                (double)timeElapseMilli / (1000 * sampleNumber) + "s/it " :   // seconds per iteration
                (double)(1000 * sampleNumber) / timeElapseMilli + "it/s";    // iteration per seconds
        return String.format("(%s / %s), %s", timeFormat(timeElapseMilli),
                timeFormat(timeRemainingMilli), extraInfo);
    }
    private String timeFormat(long millis){
        return String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
    }
}
