package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;


/**CSSpectrumManager (Crowd-Sourced Spectrum Manager) implements Shaifur's$Max's spectrum manager.
 * @author Mohammad Ghaderibaneh <mghaderibane.cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 * */
public class CSSpectrumManager {

    public double getSuMaxPower() {
        return suMaxPower;
    }

    public enum INTERPOLATION {
        IDW,
        ILDW,
        OK
    }

    private class ElementDistance {// used to find nearest objects(heap)
        int id;
        double distance;
        double power;

        ElementDistance(int id, double distance, double power) {
            this.id = id;
            this.distance = distance;
            this.power = power;
        }

        double getDistance() { return this.distance; }

        int getId() { return this.id; }

        double getPower(){ return this.power;}
    }

    private final PU[] pus;
    private final SpectrumSensor[] sss;
    private final SU[] sus;
    private final int numPusSelected;       // number of pus selected to split SSs' power
    private final int numSssSelected;       // number of SSs to do interpolation
//    private boolean pusSplit;
    private final INTERPOLATION interpolationType;
    private final double alpha;             // a parameter used for splitting and interpolation
    private final boolean detrended;        // this is used for OK meaning log-distance components would be removed
                                            // before interpolating
    private final int cellSize;
    private double suMaxPower = 0.0;

    public CSSpectrumManager(PU[] pus, SpectrumSensor[] sss, SU[] sus, int numPusSelected, int numSssSelected,
                             INTERPOLATION interpolationType, double alpha, int cellSize){
        this(pus, sss, sus, numPusSelected, numSssSelected, interpolationType, alpha, cellSize, true);
    }

    public CSSpectrumManager(PU[] pus, SpectrumSensor[] sss, SU[] sus, int numPusSelected, int numSssSelected,
                             INTERPOLATION interpolationType, double alpha, int cellSize, boolean detrended) {
        super();
        this.pus = pus;
        this.sss = sss;
        this.sus = sus;
        this.numPusSelected = numPusSelected;
        this.numSssSelected = numSssSelected;
//        this.pusSplit = pusSplit;
        this.interpolationType = interpolationType;
        this.alpha = alpha;
        this.cellSize = cellSize;
        this.detrended = detrended;

        double[][] pusSSsPL = pusSSsPathLoss();
        double[] pusSUPL = pusSUPathLoss(pusSSsPL);

        suMaxPower = suMaxPower(pusSUPL);
    }

    // path-loss values between PUs(plus active SUs) and SSs
    private double[][] pusSSsPathLoss() {
        double[][] puSSPL = new double[this.pus.length + this.sus.length - 1][this.sss.length];
//        for (double[] puSSPLrow : puSSPL)
//            Arrays.fill(puSSPLrow, Double.POSITIVE_INFINITY);
        int activePusNum = activePUs();
        for (int ssId = 0; ssId < this.sss.length; ssId++) {
            ElementDistance[] pusDistance = new ElementDistance[activePusNum + this.sus.length - 1];
            int cnt = 0;
            for (int puId = 0; puId < this.pus.length; puId++)
                if (this.pus[puId].isON())
                    pusDistance[cnt++] = new ElementDistance(puId,
                            this.sss[ssId].getRx().getElement().getLocation().mul(this.cellSize).distance(
                            this.pus[puId].getTx().getElement().getLocation().mul(this.cellSize)),
                            this.pus[puId].getTx().getPower());
            for (int suId = 0; suId < this.sus.length - 1; suId++)
                pusDistance[activePusNum + suId] = new ElementDistance(this.pus.length + suId,
                        this.sss[ssId].getRx().getElement().getLocation().mul(this.cellSize).distance(
                                this.sus[suId].getTx().getElement().getLocation().mul(this.cellSize)),
                        this.sus[suId].getTx().getPower());
            ElementDistance[] nearestPus = findNearestElements(pusDistance, this.numPusSelected);

            //check if a sensor and a PU/SU are locates in the same place
            boolean isCollocation = false;
            for (ElementDistance puSu : nearestPus){
                if (puSu.distance == 0) {
                    puSSPL[puSu.id][ssId] = 1;
                    isCollocation = true;
                    break;
                }
            }
            if (isCollocation)
                continue;           // no need to continue because all power to that ss comes from the PU/SU located at
                                    // that place.

            double totalWeight = 0.0;    // total weight of nearby pus (puPower/distance(pu, ss)
            for (ElementDistance puSu : nearestPus)
                totalWeight += WirelessTools.getDecimal(puSu.power)/
                        Math.pow(puSu.distance, this.alpha);
            for(ElementDistance pu : nearestPus)
                puSSPL[pu.id][ssId] = totalWeight /
                        (WirelessTools.getDecimal(this.sss[ssId].getRx().getReceived_power()) *
                                Math.pow(pu.distance, this.alpha));
        }
        return puSSPL;
    }

    //path-loss between PUs and requesting(last) SU
    private double[] pusSUPathLoss(double[][] pusSSPL) {
        double[] pusSUPL = new double[this.pus.length + this.sus.length - 1];  // all pus + active SUs to requesting SU
        ElementDistance[] sssDistance = new ElementDistance[this.sss.length];
        for (int ssId = 0; ssId < this.sss.length; ssId++) {
            sssDistance[ssId] = new ElementDistance(ssId,
                    this.sus[this.sus.length - 1].getTx().getElement().getLocation().mul(this.cellSize).distance(
                            this.sss[ssId].getRx().getElement().getLocation().mul(this.cellSize)), 0.0);
            // first check if a sensor and SU are located at the same place. IF yes, take that value
            if (sssDistance[ssId].distance == 0){
                for (int puId = 0; puId < this.pus.length; puId++)
                    pusSUPL[puId] = pusSSPL[puId][ssId];                                        //pus
                for (int suId = 0; suId < this.sus.length - 1; suId++)
                    pusSUPL[this.pus.length + suId] = pusSSPL[this.pus.length + suId][ssId];    //sus
                return pusSUPL;
            }
        }

        ElementDistance[] nearestSS = findNearestElements(sssDistance, this.numSssSelected);

        if (this.interpolationType == INTERPOLATION.IDW || this.interpolationType == INTERPOLATION.ILDW) {
            double totalWeight = 0.0;
            for (ElementDistance ss : nearestSS) {
                totalWeight += switch (this.interpolationType) {
                    case IDW: yield 1 / (Math.pow(ss.distance + Double.MIN_VALUE, this.alpha));
                    case ILDW: yield 1 / (Math.log10(1 + ss.distance + Double.MIN_VALUE));
                    case OK: yield 0.0;     // not applicable
                };
            }
            // calculate path-loss for pus and active sus
            for (int puId = 0; puId < this.pus.length; puId++) {
                if (this.pus[puId].isON()) {
                    for (ElementDistance ss : nearestSS) {
                        pusSUPL[puId] += pusSSPL[puId][ss.id] * switch (this.interpolationType) {
                            case IDW: yield 1 / (Math.pow(ss.distance + Double.MIN_VALUE, this.alpha));
                            case ILDW: yield 1 / (Math.log10(1 + ss.distance + Double.MIN_VALUE));
                            case OK: yield 0.0;     // not applicable
                        };
                    }
                    pusSUPL[puId] /= totalWeight;
                }
            } // end of PUs
            for (int suId = 0; suId < this.sus.length - 1; suId++){
                for (ElementDistance ss : nearestSS) {
                    pusSUPL[this.pus.length + suId] += pusSSPL[this.pus.length + suId][ss.id] *
                            switch (this.interpolationType) {
                                case IDW: yield 1 / (Math.pow(ss.distance + Double.MIN_VALUE, this.alpha));
                                case ILDW: yield 1 / (Math.log10(1 + ss.distance + Double.MIN_VALUE));
                                case OK: yield 0.0;     // not applicable
                            };
                }
                pusSUPL[this.pus.length + suId] /= totalWeight;
            }// end of active SUs
        }else if (this.interpolationType == INTERPOLATION.OK){
            double[][] nearestSsLocations = new double[nearestSS.length][2]; // x, y of nearest ss
            for (int ssi = 0; ssi < nearestSS.length; ssi++){
                int ssId = nearestSS[ssi].id;
                SpectrumSensor ss = this.sss[ssId];
                nearestSsLocations[ssi][0] = ss.getRx().getElement().getLocation().getCartesian().getX()
                        * cellSize; // x
                nearestSsLocations[ssi][1] = ss.getRx().getElement().getLocation().getCartesian().getY()
                        * cellSize; // y
            }

            //interpolated pu-ss(nearest) pl values
            LogDistancePM logDistancePM = new LogDistancePM(this.alpha);
            for (int puId = 0; puId < this.pus.length; puId++){     // pus-su interpolation
                boolean unknownValue = false;                       // if pu-ss pl is unknown, pu-su would be zero
                double[] nearestSsPuPL = new double[nearestSS.length];      // pl value for nearest ss
                for (int ssi = 0; ssi < nearestSS.length; ssi++) {
                    nearestSsPuPL[ssi] = pusSSPL[puId][nearestSS[ssi].id];
                    if (nearestSsPuPL[ssi] == 0) {
                        unknownValue = true;
                        break;
                    }
                    if (detrended) {
                        nearestSsPuPL[ssi] = -WirelessTools.getDB(nearestSsPuPL[ssi]) -
                                logDistancePM.pathLoss(this.pus[puId].getTx().getElement().getLocation().distance(
                                        this.sss[ssi].getRx().getElement().getLocation()) * this.cellSize);
                    }
                }
                if (unknownValue){
                    pusSUPL[puId] = 0;
                    continue;
                }
                SU requestingSu = this.sus[this.sus.length - 1];
                OrdinaryKriging ordinaryKriging = new OrdinaryKriging(nearestSsLocations, nearestSsPuPL,
                        requestingSu.getTx().getElement().getLocation().getCartesian().getX() * cellSize,
                        requestingSu.getTx().getElement().getLocation().getCartesian().getY() * cellSize);
                double interpolatedValue;
                try {
//                    System.out.println(Arrays.toString(nearestSsPuPL));
                    interpolatedValue = ordinaryKriging.interpolate();
                }catch (RuntimeException e){
                    interpolatedValue = Double.NEGATIVE_INFINITY;
                }
                if (detrended)
                    interpolatedValue = interpolatedValue +
                            logDistancePM.pathLoss(this.pus[puId].getTx().getElement().getLocation().distance(
                                    requestingSu.getTx().getElement().getLocation()) * this.cellSize);
                pusSUPL[puId] = WirelessTools.getDecimal(-interpolatedValue);
            }//end of pus
            for (int suId = 0; suId < this.sus.length - 1; suId++){     // sus-su interpolation
                double[] nearestSsPuPL = new double[nearestSS.length];      // pl value for nearest ss
                for (int ssi = 0; ssi < nearestSS.length; ssi++) {
                    nearestSsPuPL[ssi] = pusSSPL[this.pus.length + suId][nearestSS[ssi].id];
                    if (detrended) {
                        nearestSsPuPL[ssi] = WirelessTools.getDecimal(WirelessTools.getDB(nearestSsPuPL[ssi]) -
                                logDistancePM.pathLoss(this.sus[suId].getTx().getElement().getLocation().distance(
                                        this.sss[ssi].getRx().getElement().getLocation()) * this.cellSize));
                    }
                }
                SU requestingSu = this.sus[this.sus.length - 1];
                OrdinaryKriging ordinaryKriging = new OrdinaryKriging(nearestSsLocations, nearestSsPuPL,
                        requestingSu.getTx().getElement().getLocation().getCartesian().getX(),
                        requestingSu.getTx().getElement().getLocation().getCartesian().getY());
                double interpolatedValue = ordinaryKriging.interpolate();
                if (detrended)
                    interpolatedValue = WirelessTools.getDecimal(WirelessTools.getDB(interpolatedValue) -
                            logDistancePM.pathLoss(this.sus[suId].getTx().getElement().getLocation().distance(
                                    requestingSu.getTx().getElement().getLocation()) * this.cellSize));
                pusSUPL[this.pus.length + suId] = interpolatedValue;
            }
        }
        return pusSUPL;
    }

    //return number of active PUs
    private int activePUs(){
        int activePus = 0;
        for (PU pu : this.pus)
            if (pu.isON())
                activePus++;
        return activePus;
    }

    // consider interference to PUs only
    private double suMaxPower(double[] pusSuPL){
        double maxPower = Double.POSITIVE_INFINITY; // find the minimum possible without bringing any interference
        for (int puId = 0; puId < this.pus.length; puId++) {
            PU pu = this.pus[puId];
            if (pu.isON() && pusSuPL[puId] != 0)
                for (PUR pur : pu.getPurs()) {
                    // pur location is relational and it should be updated first
                    Element purElement = new Element(pu.getTx().getElement().getLocation().add(
                            pur.getRx().getElement().getLocation()), pur.getRx().getElement().getHeight());
                    double suPowerAtPUR = pur.getInterferenceCapacity();
                    double purSuDistance = purElement.getLocation().mul(cellSize).distance(
                            this.sus[this.sus.length - 1].getTx().getElement().getLocation().mul(cellSize));
                    double puSuDistance = pu.getTx().getElement().getLocation().mul(cellSize).distance(
                            this.sus[this.sus.length - 1].getTx().getElement().getLocation().mul(cellSize));
                    double loss = WirelessTools.getDB(pusSuPL[puId] *
                            Math.pow((Math.max(purSuDistance, 1) / Math.max(puSuDistance, 1)), this.alpha));
                    maxPower = Math.min(maxPower, suPowerAtPUR - loss);
                }
        }
        if (maxPower == Double.POSITIVE_INFINITY)
            return Double.NEGATIVE_INFINITY;
        return maxPower;
    }

    // find num nearest objects using heap
    private static ElementDistance[] findNearestElements(ElementDistance[] elements, int num){
        PriorityQueue<ElementDistance> pq = new PriorityQueue<>(num,
                Comparator.comparing(ElementDistance::getDistance).reversed());
        for (int i = 0; i < elements.length; i++){
            if (pq.size() < num)
                pq.add(elements[i]);
            else if (elements[i].distance < pq.peek().distance) {
                pq.poll();
                pq.add(elements[i]);
            }
        }
        ElementDistance[] nearest = new ElementDistance[pq.size()];
        int cnt = 0;
        for (ElementDistance elementDistance : pq)
            nearest[cnt++] = elementDistance;
        return nearest;
    }
}
