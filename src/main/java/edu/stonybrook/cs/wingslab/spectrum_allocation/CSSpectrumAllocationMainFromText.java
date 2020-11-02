package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;

public class CSSpectrumAllocationMainFromText {
    public static void main(String[] args){
        // **************** DATA SET FILES ************
        String puPath = "resources/data/su_pu_ip_based";
        String ssPath = "resources/data/su_ss_ip_based";

        // **************** SENSORS INFO ***************
        int sensorNum = 17;
        int[] selectedSensors = null;
        String SENSOR_PATH = "../commons/resources/sensors/";

        // ********************************** Field Parameters **********************************
        Shape field_shape = new Square(10);
        int cellSize = 3; //5                               // in meter


        // ********************************** CS Spectrum Manager *********************
        double csAlpha = 0.56;       // best values for splat [3.2-3.5]
        int numPusSelected = 4;
        int numSssSelected = 17;
        CSSpectrumManager.INTERPOLATION interpolationType = CSSpectrumManager.INTERPOLATION.IDW;

        long invalidSamples = 0;
        long validSamples = 0;
        double totalDiffPower = 0.0;
        double totalFPDiffPower = 0.0;
        String sensorPath = String.format("%s%s/%d/sensors.txt", SENSOR_PATH, field_shape.toString(),
                sensorNum);

        // creating sensors
        SpectrumSensor[] sss = null;
        try {
            sss = SpectrumSensor.SensorReader(sensorPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File puFile = new File(puPath);  // pu file
        File ssFile = new File(ssPath); // sensor

        try(Scanner puScanner = new Scanner(puFile); Scanner ssScanner = new Scanner(ssFile)) {
            while (puScanner.hasNext() && ssScanner.hasNext()){
                String puLine = puScanner.nextLine();
                PU[] pus = createPUs(puLine);
                SU[] sus = createSUs(puLine);
                if (selectedSensors != null){
                    try {
                        sss = SpectrumSensor.SensorReader(sensorPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                updateSSPower(ssScanner.nextLine(), sss);
                if (selectedSensors != null){
                    sss = createSelectedSensors(sss, selectedSensors);
                }
                CSSpectrumManager csSm = new CSSpectrumManager(pus, sss, sus,
                        numPusSelected, numSssSelected, interpolationType, csAlpha, cellSize);
                if (csSm.getSuMaxPower() == Double.POSITIVE_INFINITY ||
                        csSm.getSuMaxPower() == Double.NEGATIVE_INFINITY || Double.isNaN(csSm.getSuMaxPower()))
                    invalidSamples++;
                else {
                    validSamples++;

                    totalDiffPower += Math.abs(sus[sus.length - 1].getTx().getPower() - csSm.getSuMaxPower());
                    if (csSm.getSuMaxPower() > sus[sus.length - 1].getTx().getPower())
                        totalFPDiffPower += Math.abs(sus[sus.length - 1].getTx().getPower() - csSm.getSuMaxPower());
                }
            }
            System.out.printf("Valid Samples: %d%n", validSamples);
            System.out.printf("InValid Samples: %d%n", invalidSamples);
            System.out.printf("Average Difference Power: %.3f%n", totalDiffPower/validSamples);
            System.out.printf("Average FP Difference Power: %.3f%n", totalFPDiffPower/validSamples);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    private static SpectrumSensor[] createSelectedSensors(SpectrumSensor[] original, int[] selectedSensors){
        SpectrumSensor[] newSensors = new SpectrumSensor[selectedSensors.length];
        int idx = 0;
        for (int ssIdx = 0; ssIdx < original.length; ssIdx++){
            for (int ssSelectedIdx = 0; ssSelectedIdx < selectedSensors.length; ssSelectedIdx++) {
                if (selectedSensors[ssSelectedIdx] == ssIdx) {
                    newSensors[idx++] = original[ssIdx];
                    break;
                }
            }
        }
        return newSensors;
    }

    private static PU[] createPUs(String info){
        String[] columns = info.split(","); // first try to use "," as decimeter
        if (columns.length < 2)
            columns = info.split(" ");  // use space decimeter if comma does not work
        int puNum;
        try {  // an exception will be raised if the line does not provide x and y location
            puNum = (int)Double.parseDouble(columns[0].replaceAll(" ", ""));
        }
        catch (NullPointerException e){
            throw new IllegalArgumentException("File path format is not in a correct form of");
        }
        if (columns.length < puNum * 3 + 1)
            throw new IllegalArgumentException("File path format is not in a correct form of");

        PU[] pus = new PU[puNum];
        try {
            for (int puIdx = 0; puIdx < puNum; puIdx++) {
                int puX = (int)Double.parseDouble(columns[puIdx * 3 + 1].replaceAll(" ", ""));
                int puY = (int)Double.parseDouble(columns[puIdx * 3 + 2].replaceAll(" ", ""));
                double puP = Double.parseDouble(columns[puIdx * 3 + 3].replaceAll(" ", ""));
                PUR[] purs = new PUR[]{new PUR(Integer.toString(puIdx), 0, new RX(new Element(new Point(puX, puY), 0)),
                        PUR.InterferenceMethod.THRESHOLD, 1)};
                pus[puIdx] = new PU(new TX(new Element(new Point(puX, puY), 0), -puP), purs);
            }
        }catch (NullPointerException e){
            throw new IllegalArgumentException("File path format is not in a correct form (PUs)");
        }
        return pus;
    }

    private static SU[] createSUs(String info){
        String[] columns = info.split(","); // first try to use "," as decimeter
        if (columns.length < 2)
            columns = info.split(" ");  // use space decimeter if comma does not work
        int puNum;
        try {  // an exception will be raised if the line does not provide x and y location
            puNum = (int)Double.parseDouble(columns[0].replaceAll(" ", ""));
        }
        catch (NullPointerException e){
            throw new IllegalArgumentException("File path format is not in a correct form of");
        }
        int suNum;
        try {  // an exception will be raised if the line does not provide x and y location
            suNum = (int)Double.parseDouble(columns[puNum * 3 + 1].replaceAll(" ", ""));
        }
        catch (NullPointerException e){
            throw new IllegalArgumentException("File path format is not in a correct form of");
        }
        if (columns.length < (puNum + suNum) * 3 + 2)
            throw new IllegalArgumentException("File path format is not in a correct form of");
        SU[] sus = new SU[suNum];
        for (int suIdx = 0; suIdx < suNum; suIdx++){
            int suX = (int)Double.parseDouble(columns[(puNum + suIdx) * 3 + 2].replaceAll(" ", ""));
            int suY = (int)Double.parseDouble(columns[(puNum + suIdx) * 3 + 3].replaceAll(" ", ""));
            double suP = Double.parseDouble(columns[(puNum + suIdx) * 3 + 4].replaceAll(" ", ""));
            sus[suIdx] = new SU(new TX(new Element(new Point(suX, suY), 0), -suP));
        }
        return sus;
    }

    private static void updateSSPower(String info, SpectrumSensor[] sss){
        String[] columns = info.split(","); // first try to use "," as decimeter
        if (columns.length < 2)
            columns = info.split(" ");  // use space decimeter if comma does not work

        if (columns.length < sss.length)
            throw new IllegalArgumentException("NOt enough content to update sensor reading");

        try {
            for (int ssIdx = 0; ssIdx < sss.length; ssIdx++){
                double ssP = Double.parseDouble(columns[ssIdx].replaceAll(" ", ""));
                sss[ssIdx].getRx().setReceived_power(ssP);
            }

        }catch (NullPointerException e){
            throw new IllegalArgumentException("File path format is not in a correct form (sensors)");
        }

    }
}
