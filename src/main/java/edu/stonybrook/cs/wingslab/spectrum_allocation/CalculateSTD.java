package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CalculateSTD {
//    SpectrumManager spectrumManager;
    private double std;

    public CalculateSTD(PU[] pus, PropagationModel pm, Shape shape, int cellSize, double noiseFloor){
        for (PU pu : pus)
            pu.setON(true);
        SU[] sampleSU = new SU[]{new SU(new TX(new Element(shape.points(1)[0], 15.0),
                ThreadLocalRandom.current().nextDouble(-35.0, 55.0)))};
        ArrayList<Double> saValue = new ArrayList<>();
        double rEpsilon = 1.0;
        double sum = 0.0;
        int count = 0;
        SpectrumManager spectrumManager = new SpectrumManager(pus, null, null, pm, shape, cellSize, noiseFloor);
        for (int i = 0; i < 500; i++){
            sampleSU[0] = new SU(new TX(sampleSU[0].getTx().getElement().add(new
                    Point(new PolarPoint(ThreadLocalRandom.current().nextDouble(0, rEpsilon),
                    ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI)))),
                    sampleSU[0].getTx().getPower()));
            spectrumManager.setSus(sampleSU);
            try {
                double saVal = spectrumManager.computeSUMAXPower(false);
                if (saVal != Double.NEGATIVE_INFINITY) {
                    saValue.add(saVal);
                    count++;
                    sum += saVal;
                }
            }
            catch (Exception e) {
                continue;
            }
        }
        double sampleMean = sum / count;
        double sampleVar = 0.0;
        for (double val : saValue)
            sampleVar += Math.pow(val - sampleMean, 2);
        this.std = Math.pow(sampleVar/count, 0.5);
        for (PU pu : pus)
            pu.setON(false);
    }

    public double getStd() {
        return std;
    }
}
