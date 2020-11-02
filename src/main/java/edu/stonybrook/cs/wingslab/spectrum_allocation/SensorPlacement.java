package edu.stonybrook.cs.wingslab.spectrum_allocation;

import edu.stonybrook.cs.wingslab.commons.*;

import java.util.*;

/**
 * Square shape.
 * @author Mohammad Ghaderibaneh <mghaderibane@cs.stonybrook.edu>
 * @version 1.0
 * @since 1.0
 */
public class SensorPlacement {
    private final PropagationModel pm;
    private final Shape shape;
    private double[][] locationsProbability;    // a table indicating probability distribution of PU locations
    private final HashMap<Double, Double> powerProbability;   // a hash map indicating power probability(k: power, v: probability)
    private final int cellSize;
    private final int height;
    private final double cost;
    private final double std;
    private double[][] weights;

    /**
     * SensorPlacement constructor having location and power probability distribution
     * @param pm propagation model
     * @param shape shape of the area
     * @param locationsProbability location probability distribution as a 2-D array
     * @param powerProbability power probability distribution as HashMap(power as key, probability as value)
     * @param cellSize size of cells
     * @param cost sensor's cost
     * @param std standard deviation of sensors
     * @param height height of sensors as receivers*/
    public SensorPlacement(PropagationModel pm, Shape shape, double[][] locationsProbability,
                           HashMap<Double, Double> powerProbability, int cellSize, int height, double cost, double std){
        super();
        this.pm = pm;
        this.shape = shape;
        this.cellSize = cellSize;
        this.height = height;
        this.cost = cost;
        this.std = std;

        if (locationsProbability != null){
            this.locationsProbability = locationsProbability;
            if (shape.getClass() == Square.class){
                Square sq = (Square) shape;
                if (locationsProbability.length != locationsProbability[0].length |
                        locationsProbability.length != sq.getLength())
                    throw new IllegalArgumentException("Location probability table does not match with the target area.");
            }
            else if (shape.getClass() == Rectangle.class){
                Rectangle rectangle = (Rectangle) shape;
                if (locationsProbability.length != rectangle.getWidth() |
                        locationsProbability[0].length != rectangle.getLength())
                    throw new IllegalArgumentException("Location probability table does not match with the target area.");
            }
        }else{      //uniform distribution is selected when no array is passed
            if (shape.getClass() == Square.class | shape.getClass() == Rectangle.class) {
                Rectangle rectangle = (Rectangle) shape;
                this.locationsProbability = new double[rectangle.getWidth()][rectangle.getLength()];
                for (int i = 0; i < rectangle.getWidth(); i++)
                    for (int j = 0; j < rectangle.getLength(); j++)
                        this.locationsProbability[i][j] = 1.0/(rectangle.getLength() * rectangle.getWidth());
            }
        }
        this.powerProbability = powerProbability;
    }

    /**
     * SensorPlacement constructor with no location and power probability distribution
     * @param pm propagation model
     * @param shape shape of the area
     * @param cellSize size of cells
     * @param cost sensor's cost
     * @param std standard deviation of sensors
     * @param height height of sensors as receivers*/
    public SensorPlacement(PropagationModel pm, Shape shape, int cellSize, int height, double cost, double std) {
        this(pm, shape, null, null, cellSize, height, cost, std);
    }

    /**
     * SensorPlacement constructor having location probability distribution
     * @param pm propagation model
     * @param shape shape of the area
     * @param locationsProbability location probability distribution as a 2-D array
     * @param cellSize size of cells
     * @param cost sensor's cost
     * @param std standard deviation of sensors
     * @param height height of sensors as receivers*/
    public SensorPlacement(PropagationModel pm, Shape shape, double[][] locationsProbability,
                           int cellSize, int height, double cost, double std) {
        this(pm, shape, locationsProbability, null, cellSize, height, cost, std);
    }

    private List<Point> pointsInDistance(Point origin, int distance){
        int width = locationsProbability.length;    // max x-val a point can get
        int length = locationsProbability[0].length;    // max y-val a point can get
        int originX = (int)origin.getCartesian().getX();
        int originY = (int) origin.getCartesian().getY();
        List<Point> points = new ArrayList<>();
        for (int x = Math.max(0, originX - distance); x <= Math.min(originX + distance, width - 1); x++) {
            for (int y = Math.max(0, originY - distance); y <= Math.min(originY + distance, length - 1); y++){
                Point point = new Point(x, y);
                double dist = point.distance(origin);  // distance between origin and target point
                if (distance - 1 < dist & dist <= distance)
                    points.add(point);
            }
        }
        return points;

    }

    /**PRe-processing sensors' location placement. This method generate a score based on the given location probability
     * and terrain characteristic which later can be used to generate any different-number set of sensors.
     * Generating n sensors based on probability distribution and terrain information*/
    public void terrainBasedPlacementPreProcess(){
        int minDist = 1;    // 1 cell-size
        int maxDist = Math.min(locationsProbability.length, locationsProbability[0].length) / 2;

        // check every location, O(length x width)
        double sumWeight = 0.0;
        weights = new double[locationsProbability.length][locationsProbability[0].length];
        for (int x = 0; x < locationsProbability.length; x++){
            for (int y = 0; y < locationsProbability[0].length; y++){
                for (int dist = minDist; dist <= maxDist; dist++){
                    List<Point> points = pointsInDistance(new Point(x,y), dist);    // points distance away from x, y
                    for (int idx = 0; idx < Math.min(points.size(), dist + 1); idx++){
                        weights[x][y] += 1.0 / pm.pathLoss(
                                new Element(new Point(x, y).mul(cellSize), height),
                                new Element(points.get(idx).mul(cellSize), height));
                    }
                }
                sumWeight += weights[x][y];
            }
        }

        //updating weights
        for (int x = 0; x < locationsProbability.length; x++) {
            for (int y = 0; y < locationsProbability[0].length; y++) {
                weights[x][y] *= (locationsProbability[x][y] / sumWeight);
            }
        }
    }

    public void terrainBasedPlacementPreProcessHimanshu(){
        // check every location, O(length x width)
        double sumWeight = 0.0;
        weights = new double[locationsProbability.length][locationsProbability[0].length];
        for (int x = 0; x < locationsProbability.length; x++){
            for (int y = 0; y < locationsProbability[0].length; y++){
                for (int xx = 0; xx < locationsProbability.length; xx++){
                    for (int yy = 0; yy < locationsProbability[0].length; yy++){
                        if (x == xx & y == yy)
                            continue;
                        weights[x][y] += 1.0 / pm.pathLoss(
                                new Element(new Point(x, y).mul(cellSize), height),
                                new Element(new Point(xx,yy).mul(cellSize), height));
                    }
                }
                sumWeight += weights[x][y];
            }
        }

        //updating weights
        for (int x = 0; x < locationsProbability.length; x++) {
            for (int y = 0; y < locationsProbability[0].length; y++) {
                weights[x][y] *= (locationsProbability[x][y] / sumWeight);
            }
        }
    }

    /** Calculating cells' score, this method can be called to generate a set of n best sensors.
     * @param n number of sensors to be placed
     * @return a list of n placed sensors*/
    public SpectrumSensor[] terrainBasedPlacement(int n){
        return sensorGenerator(weights, n);
    }

    private SpectrumSensor[] sensorGenerator(double[][] tableScore, int n){
        SpectrumSensor[] sensors = new SpectrumSensor[n];
        //  use a max-heap to get the sensors locations
        PriorityQueue<WeightedLocations> pq = new PriorityQueue<>();
        for (int x = 0; x < locationsProbability.length; x++)
            for (int y = 0; y < locationsProbability[0].length; y++)
                pq.add(new WeightedLocations(new Point(x, y), tableScore[x][y]));
        for (int idx = 0; idx < n; idx++)
            sensors[idx] = new SpectrumSensor(new RX(new Element(Objects.requireNonNull(pq.poll()).point, height)),
                    0, 0);
        return sensors;
    }

    /**Generating n sensors based on probability distribution and terrain information
     * @param n number of sensors to be placed
     * @return a list of n placed sensors*/
    public SpectrumSensor[] probabilityBasedPlacement(int n){
        return sensorGenerator(locationsProbability, n);
    }

    private static class WeightedLocations implements Comparable<WeightedLocations>{
        Point point;
        double weight;
        WeightedLocations(Point point, double weight){
            this.point = point;
            this.weight = weight;
        }

        @Override
        public int compareTo(WeightedLocations o) {
            if (this.weight == o.weight)
                return 0;
            else if (this.weight > o.weight) //reverse order, max-pq
                return -1;
            return 1;
        }
    }

}
