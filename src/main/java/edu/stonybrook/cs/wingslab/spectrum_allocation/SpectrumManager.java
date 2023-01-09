package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;


/**SpectrumManger assign power value to a requested SU based on its current state (using PUs or SSs)
 * @author Mohammad Ghaderibaneh <mghaderibane.cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 * */
public class SpectrumManager {
    private final PU[] pus;                                 // List of PUs. they might turn off and on over samples
    private SU[] sus;                                       // List of SUs, changed over samples
    private final PropagationModel propagationModel;        // propagation model is being used
    private final int cellSize;                             // cell size
    private final SpectrumSensor[] sss;                     // List of Spectrum Sensors; they're fixed
    private final Shape shape;                              // Field's shape
    private boolean isAllowed;                              // if power of requesting is allowed. This power is ignored
                                                            // for computing maximum allowed power.
    private double suMaxPower = Double.NEGATIVE_INFINITY;   // maximum power allowed for requesting su
    private PU mostRestrictivePuIdx = null;                 // index of PU that enforces the most restrictive path-loss
                                                            // pair relation with requesting SU
    private final double noiseFloor;                        // noiseFloor
    private boolean purViolated = false;
    private double[] susOptimalPower;

    /**Spectrum Manager's constructor
     * @param pus array of PU
     * @param sus array of SU
     * @param sss array of SpectrumSensor
     * @param propagationModel PropagationModel
     * @param shape field's shape
     * @param cellSize size of each cell that represents
     * @param noiseFloor noise floor*/
    public SpectrumManager(PU[] pus, SU[] sus, SpectrumSensor[] sss, PropagationModel propagationModel,
                           Shape shape, int cellSize, double noiseFloor){
        super();
        this.pus = pus;
        this.sus = sus;
        this.sss = sss;
        this.propagationModel = propagationModel;
        this.shape = shape;
        this.cellSize = cellSize;
        this.noiseFloor = noiseFloor;
//        this.susOptimalPower = new double[5];
    }

    /**This method should be executed for each sample to compute received power for PURs and Spectrum Sensors.
     * Power values are received from ON PUs and SUs(except the last one which is requesting for power)*/
    private void computeReceivedPower(){
        computePURsReceivedPower();
        if (!this.purViolated)  // only continues if PUs do not create interference for each other
            computeSensorsReceivedPower();

    }
    // compute received power for Sensors
    private void computeSensorsReceivedPower() {
        if (this.sss == null) // if there is no sensors
            return;
        for (SpectrumSensor spectrumSensor : this.sss)
            spectrumSensor.getRx().setReceived_power(Double.NEGATIVE_INFINITY); // resetting for new computations
        computeSensorsReceivedPowerFromPUs();
        computeSensorsReceivedPowerFromSUs();
        
    }

    // compute received power for Sensors from PUs
    private void computeSensorsReceivedPowerFromPUs() {
        for (SpectrumSensor spectrumSensor : this.sss)
            for (PU pu : this.pus){
                if (pu.isON())
                    spectrumSensor.getRx().setReceived_power(powerWithPathLoss(pu.getTx(),
                            spectrumSensor.getRx()));
            }

    }

    // compute received power for Sensors from SUs
    private void computeSensorsReceivedPowerFromSUs() {
        if (this.sus == null || this.sus.length == 1) // no computations when there is no or one sensor
            return;
        for (SpectrumSensor spectrumSensor : this.sss)
            for (int i = 0; i < this.sus.length - 1; i++)
                spectrumSensor.getRx().setReceived_power(powerWithPathLoss(this.sus[i].getTx(),
                        spectrumSensor.getRx()));
    }

    // compute PUR received power
    private void computePURsReceivedPower() {
        if (this.pus == null) // if there is no PUs
            return;
        for (PU pu : this.pus)
            pu.resetPurs();
        computePURsReceivedPowerFromPUs(this.pus);
        // check if any pur is violated by another PU
        for (PU pu : this.pus)
            if (pu.isON())
                for (PUR pur : pu.getPurs())
                    if (pur.getInterferenceCapacity() == Double.NEGATIVE_INFINITY)
                        this.purViolated = true;
        if (!this.purViolated)  // continues if there is no interference to a PUR by a PU
            computePURsReceivedPowerFromSUs();
        
    }

    // compute PUR received power from PUs
    private void computePURsReceivedPowerFromPUs(PU[] pus) {
        for (PU pu : pus){
            if (pu.isON())
                for (PUR pur : pu.getPurs()) {
                    // this is done because PUR location information is relative to its PU
                    RX purRX = new RX(new Element(pu.getTx().getElement().getLocation().add(
                            pur.getRx().getElement().getLocation()),
                            pur.getRx().getElement().getHeight()));
                    pur.getRx().setReceived_power(powerWithPathLoss(pu.getTx(), purRX)); // power from its own PU
                    // now calculate power from other PUs(interference)
                    for (PU npu : pus)
                        if (npu.isON() && npu != pu) {
                            double npuPurPathLoss = this.propagationModel.pathLoss(
                                    npu.getTx().getElement().mul(this.cellSize), purRX.getElement().mul(this.cellSize));
                            pur.addInterference(npu.getPuId(), npu.getTx().getPower() - npuPurPathLoss);
                        }
                }
            }
    }

    // compute PUR received power from SUs. First, SUs power is calculated and then PURs are updated
    private void computePURsReceivedPowerFromSUs() {
        if (this.sus == null || this.sus.length == 1) // no computations when there is no or one sensor
            return;
        for (int i = 0; i < this.sus.length - 1; i++) {  // all sus' max power except the last one is calculated
            SU su = this.sus[i];
            su.getTx().setPower(computeSUMaxPower(su));
            if (su.getTx().getPower() != Double.NEGATIVE_INFINITY) // update PURs received power from SUs
                for (PU pu : this.pus)
                    for (PUR pur : pu.getPurs()){
                        // pur location is relational and it should be updated first
                        Element purElement = new Element(pu.getTx().getElement().getLocation().add(
                                pur.getRx().getElement().getLocation()), pur.getRx().getElement().getHeight());
                        double suPurPathLoss = this.propagationModel.pathLoss(su.getTx().getElement().mul(this.cellSize),
                                purElement.mul(this.cellSize));
                        pur.addInterference(su.getSuId(), su.getTx().getPower() - suPurPathLoss);

                    }
        }
    }

    // this method power value of receiver after transmitter effect. (return = rx_power + tx_power - path_loss)
    private double powerWithPathLoss(TX tx, RX rx){
        if (tx.getPower() == Double.NEGATIVE_INFINITY)
            return rx.getReceived_power();
        double loss = this.propagationModel.pathLoss(tx.getElement().mul(this.cellSize),
                rx.getElement().mul(this.cellSize));
        return WirelessTools.getDB(WirelessTools.getDecimal(tx.getPower() - loss) +
                WirelessTools.getDecimal(rx.getReceived_power()));
    }

    /**Compute maximum power allowed for the requesting SU
     * @param existingComputeSkip use true when you do not want to recompute existing(PUs and non-requesting SUs)
     *                            to speedup
     * @return maximum of the last SU in the SUs array ir provided else -inf
     * @throws RuntimeException in case of Splat! propagation model
     * @since 1.0*/
    public double computeSUMAXPower(boolean existingComputeSkip){
        this.purViolated = false;
        this.suMaxPower = Double.NEGATIVE_INFINITY;
        if (!existingComputeSkip)
            computeReceivedPower();
        if (this.sus != null && !this.purViolated){
            this.suMaxPower = computeSUMaxPower(this.sus[this.sus.length - 1]);
            this.isAllowed = this.sus[this.sus.length - 1].getTx().getPower() <= this.suMaxPower;
            return this.suMaxPower;
        }
        else
            return Double.NEGATIVE_INFINITY;
    }

    /**Compute maximum total SU powers they can send all together. This method tries to maximize total SUs power, from
     * all SUs, at the same time*/
    public String computeSUsTotalMaxPower() {
        StringBuilder susTotalInfo = new StringBuilder("");
        if (this.pus == null || this.purViolated) // if there is no PUs
            return "-Infinity";

        SU[] susTmp = new SU[this.sus.length];
        for (int i = 0; i < this.sus.length; i++){
            susTmp[i] = new SU(this.sus[i]);
        }

        double[] susLow = new double[this.sus.length];
        double[] susHigh = new double[this.sus.length];
        double diff = 0.1;  // a value that indicates when the binary search ends.
        boolean allSatisfied = false;
        Arrays.fill(susLow, this.noiseFloor);
        Arrays.fill(susHigh, 100.0);  // 100 dB power as a max value


        while (!allSatisfied){
            PU[] pusTmp = new PU[this.pus.length];
            for (int i = 0; i < this.pus.length; i++){
                pusTmp[i] = new PU(this.pus[i]);
                pusTmp[i].setON(this.pus[i].isON());
            }
            computePURsReceivedPowerFromPUs(pusTmp);

            double[] mid = new double[this.sus.length];
            System.arraycopy(susLow, 0, mid, 0, this.sus.length);
            int targetIndex = -1;
            double maxJump = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < this.sus.length; i++) {
                if (susHigh[i] - susLow[i] > maxJump) {
                    maxJump = susHigh[i] - susLow[i];
                    targetIndex = i;
                }
            }
            mid[targetIndex] = susLow[targetIndex] + (susHigh[targetIndex] - susLow[targetIndex])/2;
            for (int i = 0; i < this.sus.length; i++){
                susTmp[i].getTx().setPower(mid[i]);
            }
            if (isSUsCauseInterference(pusTmp, susTmp, this.propagationModel, this.cellSize)) {
                susHigh[targetIndex] = mid[targetIndex];
            }else {
                susLow[targetIndex] = mid[targetIndex];
            }

            allSatisfied = true;
            for (int i = 0; i < this.sus.length; i++){
                if (susHigh[i] - susLow[i] > diff){
                    allSatisfied = false;
                    break;
                }
            }
        }
        this.susOptimalPower = new double[this.sus.length];
        susTotalInfo.append(sus.length).append(",");
        for (int i = 0; i < this.sus.length - 1; i++){
            susTmp[i].getTx().setPower(susLow[i]);
            susTotalInfo.append(susTmp[i]).append(",");
            this.susOptimalPower[i] = susTmp[i].getTx().getPower();
        }
        susTmp[susTmp.length - 1].getTx().setPower(susLow[susTmp.length - 1]);
        susTotalInfo.append(susTmp[susTmp.length - 1]);
        this.susOptimalPower[susOptimalPower.length - 1] = susTmp[susTmp.length - 1].getTx().getPower();

        return susTotalInfo.toString();
    }

    private static boolean isSUsCauseInterference(PU[] pus, SU[] sus, PropagationModel propagationModel, int cellSize){
        if (sus == null) // no computations when there is no or one sensor
            return false;
        for (SU su : sus) {  // all sus' max power except the last one is calculated
            if (su.getTx().getPower() != Double.NEGATIVE_INFINITY) // update PURs received power from SUs
                for (PU pu : pus) {
                    if (!pu.isON())
                        continue;
                    for (PUR pur : pu.getPurs()) {
                        // pur location is relational and it should be updated first
                        Element purElement = new Element(pu.getTx().getElement().getLocation().add(
                                pur.getRx().getElement().getLocation()), pur.getRx().getElement().getHeight());
                        double suPurPathLoss = propagationModel.pathLoss(su.getTx().getElement().mul(cellSize),
                                purElement.mul(cellSize));
                        pur.addInterference(su.getSuId(), su.getTx().getPower() - suPurPathLoss);
                        if (pur.getInterferenceCapacity() == Double.NEGATIVE_INFINITY)
                            return true;
                    }
                }
        }
        return false;
    }

    /**will be used in case where user provides power for the requesting SU(last one of the array)
     * @return if the request is accepted
     * @since 1.0*/
    public boolean suRequestAccepted(){ return this.isAllowed; }

    /**
     * Generate information about PUs and SUs in format of "#PUs,PU1Location,PU1Power...#SUs,SU1Location...,
     * {1,0} depends on the request whether or not is allowed,"
     * @return a string consist of PUs' and SUs' information
     * @since 1.0*/
    public String pusSample(){
        // pus information
        int puOnNum = 0;                // number of ON pus
        StringBuilder puInformation = new StringBuilder(""); // better to use StringBuilder for concatenation
        for (PU pu : this.pus)
            if (pu.isON()) {
                puOnNum++;
                puInformation.append(pu).append(",");
            }
        return String.format("%1$d,%2$s%3$d,%4$s,%5$s", puOnNum, puInformation,
                this.sus.length, susInformation(), this.isAllowed ? "1":"0");
    }

    /**
     * Generate information about Spectrum Sensors and SUs in format of "#Sensor1_power,Sensor2_power...#SUs,SU1Location..."
     * @return a string consist of SSs' and SUs' information
     * @since 1.0*/
    public String susInformation(){
        // sus information
        StringBuilder suInformation = new StringBuilder("");
        for (int su = 0; su < this.sus.length - 1; su++)
            suInformation.append(this.sus[su]).append(",");
        suInformation.append(this.sus[this.sus.length - 1]);
        return suInformation.toString();
    }

    /**
     * Generate information about Spectrum Sensors and SUs in format of "#Sensor1_power,Sensor2_power...#SUs,SU1Location..."
     * @return a string consist of SSs' and SUs' information
     * @since 1.0*/
    public String sssSample(){
        // sensors information
        StringBuilder ssInformation = new StringBuilder(""); // better to use StringBuilder for concatenation
        if (this.suMaxPower != Double.NEGATIVE_INFINITY)
            for (SpectrumSensor spectrumSensor : this.sss)
                ssInformation.append(String.format("%1$.3f,", spectrumSensor.getRx().getReceived_power()));
        return String.format("%1$d,%2$s%3$d,%4$s,%5$s", this.sss.length, ssInformation,
                this.sus.length, susInformation(), this.isAllowed ? "1":"0");
    }

    /**
     * Generate information about the requesting su(last one) "#SULocation,maxPowerAllocated"
     * @return a string consist of SUs' information
     * @since 1.0*/
    public String maxPowerSample(){
        return String.format("%1$s,%2$.3f", this.sus[this.sus.length - 1].getTx().getElement().getLocation(),
                this.suMaxPower);
    }

    /**Calculate the maximum(based on minimum pur's interference capacity) power(dB) SU can send.
     * @param su requesting su*/
    private double computeSUMaxPower(SU su){
        double maxPower = Double.POSITIVE_INFINITY; // find the minimum possible without bringing any interference
        this.mostRestrictivePuIdx = null;
        for (PU pu : this.pus)
            if (pu.isON())
                for (PUR pur : pu.getPurs()) {
                    // pur location is relational and it should be updated first
                    Element purElement = new Element(pu.getTx().getElement().getLocation().add(
                            pur.getRx().getElement().getLocation()), pur.getRx().getElement().getHeight());
                    double suPowerAtPUR = Math.max(pur.getInterferenceCapacity(), noiseFloor);
                                            // interferences lower than noiseFloor is replaced by noiseFloor
                    double loss = this.propagationModel.pathLoss(su.getTx().getElement().mul(this.cellSize),
                            purElement.mul(cellSize));
                    if (suPowerAtPUR + loss < maxPower) {
                        maxPower = suPowerAtPUR + loss;
                        this.mostRestrictivePuIdx = pu;
                    }
                }
        return maxPower;
    }

    private void calculateSusSINR(){
        // ********** Calculate SINR for each SU's rx ********
        SU[] sus = this.sus;
        double[] suPowers = new double[this.sus.length];
        double error = 4.37/5;
        for (int suIdx = 0; suIdx < this.sus.length; suIdx++){
            suPowers[suIdx] = this.susOptimalPower[suIdx];// + ThreadLocalRandom.current().nextDouble(-error, 0);
        }
        for (int suIdx = 0; suIdx < sus.length; suIdx++){
            SU su = sus[suIdx];
            double pathLoss = propagationModel.pathLoss(su.getTx().getElement(), su.getRxElement());
//            double signal = WirelessTools.getDecimal(sus[suIdx].getTx().getPower() - pathLoss);
            double signal = WirelessTools.getDecimal(suPowers[suIdx] - pathLoss);
            // calculate interference from PUs and other SUs
            double totalInterference = 0.0;
            for (PU pu : pus){
                if (pu.isON()){
                    double puToSuPathLoss = propagationModel.pathLoss(pu.getTx().getElement(), su.getRxElement());
                    totalInterference += WirelessTools.getDecimal(pu.getTx().getPower() - puToSuPathLoss);
                }
            }
            // interference from other SUs
            for (int otherSuIdx = 0; otherSuIdx < sus.length; otherSuIdx++){
                if (suIdx != otherSuIdx){
                    double suToSuPathLoss = propagationModel.pathLoss(sus[otherSuIdx].getTx().getElement(),
                            su.getRxElement());
//                    totalInterference += WirelessTools.getDecimal(sus[otherSuIdx].getTx().getPower() -
//                            suToSuPathLoss);
                    totalInterference += WirelessTools.getDecimal(suPowers[otherSuIdx] -
                            suToSuPathLoss);
                }
            }
            su.setRxSINR(WirelessTools.getDB(signal / totalInterference));
        }
    }

    public double[] susDataRate(){
        if (this.purViolated)
            return new double[0];
        this.calculateSusSINR();
        SU[] sus = this.sus;
        double bandwidth = 1e6;  // in hertz
        double error = 3.4/5;//ThreadLocalRandom.current().nextDouble(-4.93, 4.93);  // in dB
        double[] dataRate = new double[sus.length];
        for (int suIdx = 0; suIdx < sus.length; suIdx++){
            double tmp = 1 + WirelessTools.getDecimal(sus[suIdx].getRxSINR() - error);
            dataRate[suIdx] = bandwidth * Math.log(tmp) / Math.log(2);
        }
        return dataRate;
    }

    // ****** Getter & Setter
    public void setSus(SU[] sus) { this.sus = sus; }

    public PU[] getPus() { return pus; }

    public SU[] getSus() { return sus; }

    public PropagationModel getPropagationModel() { return propagationModel; }

    public int getCellSize() { return cellSize; }

    public SpectrumSensor[] getSss() { return sss; }

    public Shape getShape() { return shape; }

    public PU getMostRestrictivePuIdx() { return mostRestrictivePuIdx; }

    public double getSuMaxPower() { return suMaxPower; }
}
