package edu.stonybrook.cs.wingslab.spectrum_allocation;

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

    public String getSuId() {
        return suId;
    }

    public TX getTx() {
        return tx;
    }

    /** SU's constructor.
     * @param tx SU's transmitter element*/
    public SU(TX tx){
        super();
        this.suId = String.format("SU%1$d", SU.suIntId++);
        this.tx = tx;
    }

    @Override
    public String toString(){
        return String.format("%1$s,%2$.3f", this.tx.getElement().getLocation(), this.tx.getPower());
    }
}
