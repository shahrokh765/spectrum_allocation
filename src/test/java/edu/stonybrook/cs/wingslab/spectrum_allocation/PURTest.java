package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.Element;
import edu.stonybrook.cs.wingslab.commons.Point;
import edu.stonybrook.cs.wingslab.commons.PolarPoint;
import edu.stonybrook.cs.wingslab.commons.RX;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class PURTest extends TestCase {

    @Test
    public void reset() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.addInterference("PU10", -5);
        pur1.addInterference("PU15", -7);
        pur1.reset();
        Assert.assertTrue(pur1.getInterferencePower() == Double.NEGATIVE_INFINITY);
    }

    @Test
    public void getInterferencePowerFrom() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.addInterference("PU10", -5);
        Assert.assertTrue(pur1.getInterferencePowerFrom("PU10") == -5.0);
        Assert.assertTrue(pur1.getInterferencePowerFrom("PU11") == Double.NEGATIVE_INFINITY);
    }

    @Test
    public void addInterference() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.addInterference("PU10", -5);
        pur1.addInterference("PU10", -5);
        System.out.println(pur1.getInterferencePower());
    }

    @Test
    public void updateInterference() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.addInterference("PU10", -5);
        Assert.assertTrue(pur1.getInterferencePowerFrom("PU10") == -5.0);
        pur1.updateInterference("PU10", -7);
        Assert.assertTrue(pur1.getInterferencePowerFrom("PU10") == -7.0);
        pur1.updateInterference("PU11", -5);
    }

    @Test
    public void deleteInterferencePowerFrom() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.addInterference("PU10", -5);
        Assert.assertTrue(pur1.getInterferencePowerFrom("PU10") == -5.0);
        pur1.deleteInterferencePowerFrom("PU10");
        Assert.assertTrue(pur1.getInterferencePowerFrom("PU10") == Double.NEGATIVE_INFINITY);
    }

    @Test
    public void getInterferenceCapacity() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.BETA, 1.2);
        pur1.getRx().setReceived_power(0);
        pur1.addInterference("PU10", -5);
        System.out.println(pur1.getInterferenceCapacity());
        pur1.addInterference("PU12", -5);
        System.out.println(pur1.getInterferenceCapacity());
    }

    @Test
    public void testToString() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(new PolarPoint(10, 0.5)), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.getRx().setReceived_power(0.5);
        pur1.addInterference("PU5", -6.4);
        pur1.addInterference("PU4", -6.4);
        System.out.println(pur1);
        PUR pur2 = new PUR("PU11", 3, new RX(new Element(new Point(new PolarPoint(5, 0.2)), 15)),
                PUR.InterferenceMethod.BETA, 1.1);
        System.out.println(pur2);
    }

    @Test
    public void setRx() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(new PolarPoint(10, 0.5)), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        System.out.println(pur1.getRx());
        pur1.setRx(new RX(new Element(new Point(new PolarPoint(5, 0.5)), 5)));
        System.out.println(pur1.getRx());

    }

    @Test
    public void getBetaThreshold() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(new PolarPoint(10, 0.5)), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        Assert.assertTrue(pur1.getBetaThreshold() == PUR.InterferenceMethod.THRESHOLD);
        PUR pur2 = new PUR("PU10", 2, new RX(new Element(new Point(new PolarPoint(10, 0.5)), 15)),
                PUR.InterferenceMethod.BETA, 1.2);
        Assert.assertTrue(pur2.getBetaThreshold() == PUR.InterferenceMethod.BETA);
    }

    @Test
    public void getBetaThresholdValue() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(new PolarPoint(10, 0.5)), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        Assert.assertTrue(pur1.getBetaThresholdValue() == 1.2);
        PUR pur2 = new PUR("PU10", 2, new RX(new Element(new Point(new PolarPoint(10, 0.5)), 15)),
                PUR.InterferenceMethod.BETA, 1.25);
        Assert.assertTrue(pur2.getBetaThresholdValue() == 1.25);
    }

    @Test
    public void getInterferencePower() {
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        pur1.addInterference("PU10", -5);
        Assert.assertTrue(pur1.getInterferencePower() == -5.0);
    }

    @Test
    public void createPURs() {
        java.awt.Point point = new java.awt.Point();
        PUR[] purs = PUR.createPURs("PU10", 100, PUR.InterferenceMethod.BETA,
                1.5,5, 10,  15.0);
        for (PUR pur : purs)
            System.out.println(pur);
    }

    @Test
    public void testCopyConstructor(){
        PUR pur1 = new PUR("PU10", 2, new RX(new Element(new Point(10, 5), 15)),
                PUR.InterferenceMethod.THRESHOLD, 1.2);
        PUR pur2 = new PUR(pur1);
        Assert.assertNotSame(pur1, pur2);
        Assert.assertNotSame(pur1.getRx(), pur2.getRx());
        System.out.println(pur1);
        System.out.println(pur2);
    }
}