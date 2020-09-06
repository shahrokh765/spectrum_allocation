package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;
import junit.framework.TestCase;

import java.util.concurrent.ThreadLocalRandom;

public class SpectrumManagerTest extends TestCase {
    private PU[] createPus(Shape shape,int n){
        PU[] pus = new PU[n];
        double minPower = 0;
        double maxPower = 30;
        int i = 0;
        for (Point point : shape.points(n)){
            pus[i] = new PU(new TX(new Element(point, 15),
                    minPower + (maxPower - minPower) * Math.random()), 10,
                    PUR.InterferenceMethod.BETA, 1.0, 1, 3, 15);
//                    pus[i].setON(Math.random() >= 0.5);
            i++;
        }
        return pus;
    }

    private SpectrumSensor[] createSS(Shape shape, int n){
        SpectrumSensor[] sss = new SpectrumSensor[n];
        int i = 0;
        for (Point point : shape.pointsUniform(n))
            sss[i++] = new SpectrumSensor(new RX(new Element(point, 15)), 1.0, 1.2);
        return sss;
    }

    private SU[] createSUs(Shape shape, int n){
        SU[] sus = new SU[n];
        double minPower = -30;
        double maxPower = 0;
        int i = 0;
        for (Point point : shape.points(n)){
            sus[i++] = new SU(new TX(new Element(shape.points(1)[0], 15),
                    minPower + (maxPower - minPower) * Math.random()));
        }
        return sus;
    }
    public void testComputeSUMAXPower() {
        Shape shape = new Square(1000);
        SpectrumManager spectrumManager = new SpectrumManager(
                createPus(shape,15), createSUs(shape, 1), createSS(shape, 400),
                new LogDistancePM(3, 1), shape, 1, -90.0);
        System.out.println(spectrumManager.computeSUMAXPower(false));
    }

    public void testSuRequestAccepted() {
        Shape shape = new Square(1000);
        SpectrumManager spectrumManager = new SpectrumManager(
                createPus(shape,15), createSUs(shape, 1), createSS(shape, 400),
                new LogDistancePM(3, 1), shape, 1, -90.0);
        spectrumManager.computeSUMAXPower(false);
        System.out.println(spectrumManager.suRequestAccepted());
    }

    public void testPusSample() {
        Shape shape = new Square(1000);
        SpectrumManager spectrumManager = new SpectrumManager(
                createPus(shape,15), createSUs(shape, 1), createSS(shape, 400),
                new LogDistancePM(3, 1), shape, 1, -90.0);
        spectrumManager.computeSUMAXPower(false);
        System.out.println(spectrumManager.pusSample());
    }

    public void testSssSample() {
        Shape shape = new Square(1000);
        SpectrumManager spectrumManager = new SpectrumManager(
                createPus(shape,15), createSUs(shape, 1), createSS(shape, 400),
                new LogDistancePM(3, 1), shape, 1, -90.0);
        spectrumManager.computeSUMAXPower(false);
        System.out.println(spectrumManager.sssSample());
    }

    public void testRequestSample() {
        Shape shape = new Square(1000);
        SpectrumManager spectrumManager = new SpectrumManager(
                createPus(shape,15), createSUs(shape, 1), createSS(shape, 400),
                new LogDistancePM(3, 1), shape, 1, -90.0);
        spectrumManager.computeSUMAXPower(false);
        System.out.println(spectrumManager.pusSample());
        System.out.println(spectrumManager.maxPowerSample());
    }
}