package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.Element;
import edu.stonybrook.cs.wingslab.commons.Point;
import edu.stonybrook.cs.wingslab.commons.TX;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class PUTest {

    @Test
    public void resetPurs() {
        PU pu1 = new PU(new TX(new Element(new Point(5, 6), 14), -5.5), 10,
                PUR.InterferenceMethod.BETA, 2.0, 5, 10, 10);
        System.out.println(pu1);
        pu1.getPurs()[0].addInterference("PU100", 14);
        pu1.resetPurs();
        for (int i = 0; i < pu1.getPurs().length; i++) {
            Point pur_location = pu1.getTx().getElement().getLocation().add(
                    pu1.getPurs()[i].getRx().getElement().getLocation());
            System.out.println(String.format("%1$s \n(%2$s, %3$.3f)", pu1.getPurs()[i], pur_location,
                    pu1.getTx().getElement().getLocation().distance(pur_location)));
        }
    }

    @Test
    public void testToString() {
        System.out.println(Double.NEGATIVE_INFINITY);
    }

    @Test
    public void testCopyConstructor(){
        PU pu1 = new PU(new TX(new Element(new Point(5, 6), 14), -5.5), 10,
                PUR.InterferenceMethod.BETA, 2.0, 5, 10, 10);
        PU pu2 = new PU(pu1);
        Assert.assertNotSame(pu1, pu2);
        Assert.assertNotSame(pu1.getTx(), pu2.getTx());
        Assert.assertNotSame(pu1.getTx().getElement(), pu2.getTx().getElement());
        Assert.assertNotSame(pu1.getTx().getElement().getLocation(), pu2.getTx().getElement().getLocation());
        for (int i = 0; i < pu1.getPurs().length; i++) {
            Assert.assertNotSame(pu1.getPurs()[i], pu2.getPurs()[i]);
            Assert.assertEquals(pu1.getPurs()[i].getPurId(), pu2.getPurs()[i].getPurId());
        }
        System.out.println(pu1);
        System.out.println(pu2);
    }

}