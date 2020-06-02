package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.Element;
import edu.stonybrook.cs.wingslab.commons.Point;
import edu.stonybrook.cs.wingslab.commons.TX;

/**
 * Primary User class including a TX and a set of PURs.
 * @author Mohammad Ghaderibaneh <mghaderibane@cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 * */
public class PU {
    private TX tx;              // PU's transmitter
    private final PUR[] purs;   // list of PURs
    private final String puId;  // pu id in String; will be used for PURs' interference
    private static int puIntId = 0;  // pu id
    private boolean ON = true; // defines if PU contributes in a spectrum allocation sample; it is ON by default

    /**
     * PU constructor when list of purs along with its transmitter are passed.
//     * @param puIntId PU's id; should be unique inter PUs
     * @param tx PU's transmitter(TX)
     * @param purs list of its PURs
     * @since 1.0
     * */
    public PU(TX tx, PUR[] purs){
        super();
//        this.puIntId = puIntId;
        this.puId = String.format("PU%1o", PU.puIntId++);
        this.tx = tx;
        this.purs = purs;
    }
    
    /**Copy constructor that gets a PU object and create a new one with the same values.*/
    public PU(PU pu){
        super();
        this.puId = pu.puId;
        this.tx = new TX(new Element(new Point(pu.tx.getElement().getLocation().getCartesian()),
                pu.tx.getElement().getHeight()), pu.tx.getPower());
        this.purs = new PUR[pu.purs.length];
        int purId = 0;
        for (PUR pur : pu.purs)
            this.purs[purId++] = new PUR(pur);

    }

    /**
     * PU constructor when only number of purs along with its transmitter are passed.
     * PURs are created with a random distance [purMinDist, purMaxDist] uniformly(angle) around PU.
//     * @param puIntId PU's id; should be unique inter PUs
     * @param tx PU's transmitter(TX)
     * @param purNum number of PURs
     * @param betaThreshold interference calculation method
     * @param betaThresholdValue interference calculation value
     * @param purMinDist minimum pur distance
     * @param purHeight maximum pur distance
     * @since 1.0
     * */
    public PU(TX tx, int purNum, PUR.InterferenceMethod betaThreshold,
              double betaThresholdValue, double purMinDist, double purMaxDist, double purHeight){
        super();
//        this.puIntId = puIntId;
        this.puId = String.format("PU%1o", PU.puIntId++);
        this.tx = tx;
        this.purs = PUR.createPURs(this.puId, purNum, betaThreshold, betaThresholdValue, purMinDist,
                purMaxDist, purHeight);
    }

    /**
     * reset(clear received power values) all purs of PU.
     * @since 1.0*/
    public void resetPurs(){
        for (PUR pur : this.purs)
            pur.reset();
    }

    @Override
    public String toString(){
        return String.format("%1$s,%2$.3f", this.tx.getElement().getLocation(), this.tx.getPower());
    }

    public TX getTx() { return tx; }

    public PUR[] getPurs() { return purs; }

    public String getPuId() { return puId; }

    public boolean isON() { return ON; }

    public void setTx(TX tx) { this.tx = tx; } // TODO consider deleting setter

    public void setON(boolean ON) { this.ON = ON; }
}
