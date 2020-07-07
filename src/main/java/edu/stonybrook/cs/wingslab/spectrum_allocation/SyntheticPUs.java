package edu.stonybrook.cs.wingslab.spectrum_allocation;

import java.util.concurrent.ThreadLocalRandom;

/**
 * SyntheticPUs class creating new samples based on r_max and most restrictive PU.
 * @author Mohammad Ghaderibaneh <mghaderibane@cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 * */
public class SyntheticPUs {
    private PU[] newPus;
    private boolean valid;

    /**
     * constructor. processing is happened here.
     * @param rMax maximum transmission distance
     * @param pus array of existing pus
    */
    public SyntheticPUs(double rMax, PU[] pus, PU mostRestrictivePU, double minPowerAllowed, int cellSIze){
        valid = false;
        int numberActivePus = 0;
        for (PU pu : pus)
            if (pu.isON())
                numberActivePus++;

        newPus = new PU[numberActivePus];
        int puIdx = 0;
        for (PU pu : pus)
            if (pu.isON()){
                newPus[puIdx] = new PU(pu);
                if (pu.getTx().getElement().getLocation().distance(mostRestrictivePU.getTx().getElement().getLocation())
                * cellSIze > rMax) {  // if distance > rMax, we can decrease the power
                    newPus[puIdx].getTx().setPower(ThreadLocalRandom.current().nextDouble(minPowerAllowed,
                            pu.getTx().getPower()));
                    valid = true;
                }
                puIdx++;
            }
    }

    public PU[] getNewPus() {
        return newPus;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public String toString(){
        StringBuilder puInformation = new StringBuilder(""); // better to use StringBuilder for concatenation
        for (PU pu : this.newPus)
            if (pu.isON())
                puInformation.append(pu).append(",");
        return String.format("%1$d,%2$s", this.newPus.length, puInformation);
    }
}
