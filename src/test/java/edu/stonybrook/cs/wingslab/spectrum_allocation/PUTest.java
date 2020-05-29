package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.Element;
import edu.stonybrook.cs.wingslab.commons.Point;
import edu.stonybrook.cs.wingslab.commons.TX;
import junit.framework.TestCase;
import org.junit.Test;

public class PUTest extends TestCase {

    @Test
    void resetPurs() {
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
}