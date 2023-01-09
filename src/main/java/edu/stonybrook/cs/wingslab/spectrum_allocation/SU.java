package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.Element;
import edu.stonybrook.cs.wingslab.commons.Point;
import edu.stonybrook.cs.wingslab.commons.TX;

/**Secondar User(SU) whose request is processed by SpectrumManager to allocate spectrum.
 * @author Mohammad Ghaderibaneh <mghaderibane.cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 * */
public class SU {
    private static int suIntId = 0;    // internal id for each SU object
    private final String suId;    // unique id
    private final TX tx;          // SU's transmitter
    // add an RX to SU to calculate SINR
    private final Element rxElement;
    private double rxSINR;  // sinr to receiver point in dB

    public String getSuId() {
        return suId;
    }

    public TX getTx() {
        return tx;
    }

    /** SU's constructor.
     * @param tx SU's transmitter element*/
    public SU(TX tx){
//        super();
//        this.suId = String.format("SU%1$d", SU.suIntId++);
//        this.tx = tx;
        this(tx, null);
    }

    /** SU's constructor.
     * @param tx SU's transmitter element*/
    public SU(TX tx, Element rxElement){
        super();
        this.suId = String.format("SU%1$d", SU.suIntId++);
        this.tx = tx;
        this.rxElement = rxElement;
        this.rxSINR = 0;
    }

    /** Copy constructor*/
    public SU(SU su){
        super();
        this.suId = su.suId;
        this.tx = new TX(new Element(new Point(su.tx.getElement().getLocation().getCartesian()),
                su.tx.getElement().getHeight()), su.tx.getPower());
        this.rxElement = new Element(new Point(su.rxElement.getLocation().getCartesian()), su.rxElement.getHeight());
        this.rxSINR = su.rxSINR;
    }

    @Override
    public String toString(){
        return String.format("%1$s,%2$.3f", this.tx.getElement().getLocation(), this.tx.getPower());
    }

    public Element getRxElement() {
        return this.rxElement;
    }

    public double getRxSINR() {
        return rxSINR;
    }

    public void setRxSINR(double rxSINR) {
        this.rxSINR = rxSINR;
    }
}
