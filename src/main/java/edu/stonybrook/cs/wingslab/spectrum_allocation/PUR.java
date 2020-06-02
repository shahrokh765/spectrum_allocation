package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Primary User Receiver class.
 * @author Mohammad Ghaderibaneh <mghaderibane@cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 * */
public class PUR {
    /**
     * interferenceMethod defines the method for calculating interference.
     * THRESHOLD: power received from other PUs must be less than a threshold(dB) value (irp < threshold).
     * BETA: Power from its PU and other cumulative PUs must be less than a beta value (rp/irp > beta)
     * @version 1.0
     * @since 1.0
     * */
    public enum InterferenceMethod {
        THRESHOLD,
        BETA
    }

    private InterferenceMethod BetaThreshold;
    private int purIntId;  // an internal id for PURs of a PU
    private String purId; // Global unique id combined of PU and PUR id
    private RX rx;  // PUR's receiver object(location and received power from its PU)
    private double betaThresholdValue;  // a float value for Beta/Threshold whatever is selected
    private double irp; //total power(dB) received from other pus and sus ipr=total(val in irp_map)
    private HashMap<String, Double> irpMap; //a maps to store key/val where key is id of other PUs and SUs and val is their power(dB)

    /**
     * PUR constructor
     * @since 1.0
     * @param puId PU's id to generate a unique id for PUR
     * @param purIntId -th PUR of the PU
     * @param rx RX element(location and received power) of PUR. Location is relative to PU's location defined in Polar coordination
     * @param betaThreshold defines if beta or threshold calculation is used
     * @param betaThresholdValue value assigned to betaThreshold(BETA must not be zero)
     * */
    public PUR(String puId, int purIntId, RX rx, InterferenceMethod betaThreshold, double betaThresholdValue){
        super();
        if (betaThreshold == InterferenceMethod.BETA && betaThresholdValue == 0.0)
            throw new IllegalArgumentException("BETA cannot be zero.");
        this.purIntId = purIntId;
        this.purId = String.format("%1$s_PUR%2$d", puId, purIntId);
        this.BetaThreshold = betaThreshold;
        this.betaThresholdValue = betaThresholdValue;
        this.rx = rx;
        this.rx.setReceived_power(Double.NEGATIVE_INFINITY);
        this.irp = Double.NEGATIVE_INFINITY;
        this.irpMap = new HashMap<>();
    }

    /**Copy constructor which takes a PUR object and create a new one*/
    public PUR(PUR pur){
        super();
        this.purIntId = pur.purIntId;
        this.purId = pur.purId;
        this.BetaThreshold = pur.BetaThreshold;
        this.betaThresholdValue = pur.betaThresholdValue;
        this.rx = new RX(new Element(new Point(pur.rx.getElement().getLocation().getCartesian()),
                        pur.rx.getElement().getHeight()));
        this.rx.setReceived_power(Double.NEGATIVE_INFINITY);
        this.irp = Double.NEGATIVE_INFINITY;
        this.irpMap = new HashMap<>();
    }

    /**
     * reset all received power values(rp and irp)
     * @since 1.0*/
    public void reset(){
        this.irp = Double.NEGATIVE_INFINITY;
        this.irpMap = new HashMap<>();
        this.rx.setReceived_power(Double.NEGATIVE_INFINITY);
    }

    /**Return power(dB) received from key to PUR. If it doesn't exist, -inf would be returned
     * @return interference power from PU
     * @since 1.0*/
    public double getInterferencePowerFrom(String key){
        if (this.irpMap.containsKey(key))
            return this.irpMap.get(key);
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * Adds power value from a new PU. If it already exists, a warning will be raised.
     * @param key PU's id
     * @param value power value
     * @since 1.0
     * */
    public void addInterference(String key, double value){
        if (this.irpMap.containsKey(key)) {
            Logger logger = Logger.getLogger(PUR.class.getName());
            logger.warning(String.format("Power for %1$s element already exists in %2$s.", key, this.purId)
                    + " Use updateInterference(key, value) if you need to update power for an element.");
            return;
        }
        this.irpMap.put(key, value);
        this.irp = WirelessTools.getDB(WirelessTools.getDecimal(this.irp) +
                WirelessTools.getDecimal(value));
    }

    /**
     * Adds power value from a new PU. If it already exists, a warning will be raised.
     * @param key PU's id
     * @param value power value
     * @since 1.0
     * */
    public void updateInterference(String key, double value){
        if (!this.irpMap.containsKey(key)) {
            Logger logger = Logger.getLogger(PUR.class.getName());
            logger.warning(String.format("Power for %1$s element does not exist in  %2$s.", key, this.purId)
                    + " Use addInterference(key, value) if you need to add power for an element.");
            return;
        }
        double oldValue = this.irpMap.put(key, value);
        this.irp = WirelessTools.getDB(WirelessTools.getDecimal(this.irp) +
                WirelessTools.getDecimal(value));

        this.irpMap.put(key, value);
        this.irp = WirelessTools.getDB(WirelessTools.getDecimal(this.irp) +
                WirelessTools.getDecimal(value) -
                WirelessTools.getDecimal(oldValue));
    }

    /**
     * Delete power associated to PU key if it exists.
     * @param key PU id to delete its power*/
    public void deleteInterferencePowerFrom(String key){
        if (this.irpMap.containsKey(key)){
            double value = this.irpMap.remove(key);
            this.irp = WirelessTools.getDB(WirelessTools.getDecimal(this.irp) -
                    WirelessTools.getDecimal(value));
        }
    }

    /**
     * Calculate the maximum extra interference(dB) that PUR can stands.
     * @return allowed interference power
     * */
    public double getInterferenceCapacity(){
        double irpDecimal = WirelessTools.getDecimal(this.irp);
        if (this.BetaThreshold == InterferenceMethod.BETA) { // using BETA
            double rpDecimal = WirelessTools.getDecimal(this.rx.getReceived_power());
            return WirelessTools.getDB(rpDecimal / this.betaThresholdValue - irpDecimal);
        }else {//using THRESHOLD
            double thresholdDecimal = WirelessTools.getDecimal(this.betaThresholdValue);
            return WirelessTools.getDB(thresholdDecimal - irpDecimal);
        }
    }

    @Override
    public String toString(){
        double r = this.rx.getElement().getLocation().getPolar().getR();
        double theta = this.rx.getElement().getLocation().getPolar().getTheta();
        String interferenceMsg = switch (this.BetaThreshold){
            case BETA -> "\nbeta= ";
            case THRESHOLD -> "\nthreshold= ";
        };
        interferenceMsg = interferenceMsg + String.format("%1$.3f", this.betaThresholdValue);
        return String.format("\nid= %1$s", this.purId) +
                String.format("\nrelative location= (%1$.3f, %2$.3f)", r, theta) +
                String.format("\nheight= %1.3f", this.rx.getElement().getHeight()) +
                interferenceMsg +
                String.format("\nreceived power(dB)= %1$.3f", this.rx.getReceived_power()) +
                String.format("\nreceived interference= %1$.3f", this.irp);
    }

    /**
     * Create an array of PURs.
     * PURs are created with a random distance [purMinDist, purMaxDist] uniformly(angle) around PU.
     * @param puId PU's id; should be unique inter PUs
     * @param purNumber number of PURs
     * @param purMinDist minimum distance for a PUR
     * @param purMaxDist maximum distance for a PUR
     * @param betaThreshold interference calculation method
     * @param betaThresholdValue value for interference calculation
     * @param purHeight height of PURs
     * @return an array of PURs
     * @since 1.0
     * */
    public static PUR[] createPURs(String puId, int purNumber,
                                   InterferenceMethod betaThreshold, double betaThresholdValue,
                                   double purMinDist, double purMaxDist,
                                   double purHeight){
        if (purMinDist > purMaxDist)
            throw new IllegalArgumentException("Minimum PUR distance is greater than maximum");
        PUR[] purs = new PUR[purNumber];
        Random rand = new Random();
        double angle = Math.toRadians(360.0 / purNumber);
        for (int i = 0; i < purNumber; i++){
            purs[i] = new PUR(puId, i,
                    new RX(new Element(new Point(
                            new PolarPoint(purMinDist + (purMaxDist - purMinDist) * rand.nextDouble(),
                                    i * angle)),
                            purHeight)),
                    betaThreshold, betaThresholdValue);
        }
        return purs;
    }

    public void setRx(RX rx) { this.rx = rx; }

    public InterferenceMethod getBetaThreshold() { return BetaThreshold; }

    public String getPurId() { return purId; }

    public RX getRx() { return rx; }

    public double getBetaThresholdValue() { return betaThresholdValue; }

    /** Return the total power(dB) it receives from other elements(PUs, SUs).*/
    public double getInterferencePower() { return irp; }
}
