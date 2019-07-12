package gov.nasa.worldwindx.applications;

import gov.nasa.worldwind.*;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.*;
import gov.nasa.worldwind.globes.*;
import gov.nasa.worldwind.layers.*;
import gov.nasa.worldwind.render.*;
import gov.nasa.worldwind.render.airspaces.*;
import gov.nasa.worldwind.util.*;
import gov.nasa.worldwindx.examples.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

public class FlightModel extends ApplicationTemplate
{
    private static ArrayList<Flight> flights; // ArrayList of all flight objects
    private static ArrayList<Flight> dropOutFlights = new ArrayList<Flight>(); // ArrayList created in loadFlights using a percentage threshold for simulating planes dropping out
    private static int startTime;
    private static int endTime;
    private static float dropOutThreshold;
    private static Earth earth = new Earth();
    private static final long COMMS_RADIUS = 386243; //386243; // The radius for how far the communications reach
    private static final long ALTITUDE = 12192; //12192; //The altitude we consider planes to be flying at
    private static final long GROUND_COMMS_RADIUS = 386049; //386049
    private static final double US_CENTER_LAT = 39.833333;
    private static final double US_CENTER_LONG = -98.583333;
    private static Material red = new Material(Color.red);
    private static Material blue = new Material(Color.blue);
    private static final Object repaintLock = new Object();
    private static final Object fileLock = new Object();
    private static long mins;
    private static long hours;
    private static long day;
    private static double[][] graph;
    private static double unweightedDistance;
    private static double weightedDistance;
    private static int unweightedTransmissions;
    private static int weightedTransmissions;
    private static long sizeOfNetwork;
    private static long nodes;
    private static long connections;
    private static ImageProcessor ip = new ImageProcessor();

    public static void main(String[] args) {
        sanitizeArgs(args);
        flights = loadFlightData(startTime, endTime, dropOutThreshold);
        start("NSRI World Wind Flight Model", FlightModel.AppFrame.class);
    }

    private static void sanitizeArgs(String[] args){
        try{
            startTime = Integer.parseInt(args[0]);
        }catch (NumberFormatException nfeStartTime){
            startTime = 0;
            System.err.println("Invalid start time entered");
        }

        try{
            endTime = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfeEndTime){
            endTime = 0;
            System.err.println("Invalid end time entered");
        }

        try{
            dropOutThreshold = Float.parseFloat(args[2]);
        }catch (NumberFormatException nfeDropOut){
            dropOutThreshold = 1;
            System.err.println("Invalid drop out threshold entered");
        }

        if(startTime < 0){
            startTime = 0;
        }
        if(endTime < startTime){
            endTime =  startTime;
        }

        if(dropOutThreshold < 0){
            dropOutThreshold = 0;
        } else if(dropOutThreshold > 1){
            dropOutThreshold = 1;
        }
    }

    /* Using a csv datafile it loads all of the given flight data for a certain day into the array of flight objects .
       Method implements a try catch statement to prevent file not found error.
       Flight data consists of startingLatitude, startingLongitude, startTime, endingLatitude, endingLongitude, endTime.

        @return the array list of all of the given flights
     */
    private static ArrayList<Flight> loadFlightData(long startTime, long endTimeX, float dropOutThreshold) {
        ArrayList<Flight> flights = new ArrayList<Flight>();
        Random dropOutPercentage = new Random();
        float latestEndTime = 0;
        try {
            File data = new File("./data/data.csv");
            BufferedReader br = new BufferedReader(new FileReader(data));
            br.readLine(); // Disposes of the header in the CSV

            String line = br.readLine();
            String[] lineStrings = line.split(",");
            float[] lineData = new float[6];
            for (int i = 0; i < lineStrings.length; i++) {
                lineData[i] = Float.parseFloat(lineStrings[i]);
            }
            line = br.readLine();
            long counter = 0;
            while (lineData[2] <= endTimeX && line != null)  {
                if (lineData[5] >= startTime) {
                    counter++;
                    Flight flight = new Flight(lineData[0], lineData[1], lineData[2], lineData[3], lineData[4], lineData[5]);
                    flights.add(flight);
                    if(lineData[5] > latestEndTime){
                        latestEndTime = lineData[5];
                    }
                }
                lineStrings = line.split(",");
                lineData = new float[6];
                for (int i = 0; i < lineStrings.length; i++) {
                    lineData[i] = Float.parseFloat(lineStrings[i]);
                }
                line = br.readLine();
            }
            if(latestEndTime < endTimeX){
                endTime = Math.round(latestEndTime) + 1;
            }
            System.out.println("Loaded " + counter + " flights from dataset.");
        } catch (IOException e) {
            System.err.println("Failed to load flight data");
        }

        for(Flight f : flights ){
            if(dropOutPercentage.nextFloat() < dropOutThreshold){
                dropOutFlights.add(f);
            }
        }

        return dropOutFlights;
    }


    public static class AppFrame extends ApplicationTemplate.AppFrame {
        final WorldWindow wwd = this.getWwd();
        JPanel updatePanel = new JPanel();
        final LayerList layers = wwd.getModel().getLayers();
        final View view = wwd.getView();
        final JTextField timeField = new JTextField();
        String updatedTime;
        int startPlane;
        int endPlane;
        ArrayList<Node> rendered = new ArrayList<Node>();
        ArrayList<ArrayList<Integer>> paths = new ArrayList<ArrayList<Integer>>();
        PointPlacemark startPin;
        PointPlacemark endPin;
        boolean nodesEnabled = false;
        boolean connectionsEnabled = false;
        boolean groundCoverageEnabled = false;
        boolean largestNetworkEnabled = false;
        boolean largestNetworkWeightedEnabled = false;
        boolean shortestDistanceEnabled = false;
        boolean fewestTransmissionsEnabled = false;
        boolean dataBubbleEnabled = false;

        public AppFrame() {
            view.setEyePosition(Position.fromDegrees(US_CENTER_LAT, US_CENTER_LONG, 10000000));
            view.setHeading(Angle.fromDegrees(0));
            timeField.setText(String.format("%d", startTime));
            hours = (startTime / 60) % 24;
            day = (startTime / 1440) + 1;
            mins = startTime % 60;
            updatedTime = String.format("2/%d/19   |   %02d : %02d UTC" ,day ,hours, mins );
            startPin = new PointPlacemark(Position.fromDegrees(US_CENTER_LAT, US_CENTER_LONG));
            endPin = new PointPlacemark(Position.fromDegrees(US_CENTER_LAT, US_CENTER_LONG));
            startPlane = findNearestPlane(startPin.getPosition());
            endPlane = findNearestPlane(endPin.getPosition());
            layers.add(makeFlightPaths());
            layers.add(makePathSelection());
            layers.add(makePlanes(startTime));
            layers.add(makeConnections());
            layers.add(makeGroundCoverage());
            layers.add(largestNetwork());
            layers.add(largestNetworkWeighted());
            if (startPlane != -1 && endPlane != -1) {
                layers.add(makeShortestDistance());
                layers.add(makeFewestTransmissions());
            }
            layers.add(makeDataBubble());
            updatePanel.setPreferredSize(new Dimension(200, 80));
            timeField.setToolTipText("Enter a time");
            timeField.setPreferredSize(new Dimension(100, 25));
            JButton updateButton = new JButton("Update");
            wwd.addSelectListener(new BasicDragger(wwd));
            updateButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {updateAirTrafficModel(timeField.getText());}
            });
            updatePanel.add(updateButton);
            JButton renderButton = new JButton("Render");
            renderButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    renderImages();
                }
            });
            updatePanel.add(renderButton);
            JButton percentageButton = new JButton("Percentage");
            percentageButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    percentageTrials();
                }
            });
            updatePanel.add(percentageButton);
            JButton coverageButton = new JButton("Coverage");
            coverageButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    coverageTrials();
                }
            });
            updatePanel.add(coverageButton);
            updatePanel.add(timeField, 0);
            this.getControlPanel().add(updatePanel, BorderLayout.SOUTH);
            this.setExtendedState(Frame.MAXIMIZED_BOTH);
        }

        // Layer that adds the pins for the use of the shortest path
        private Layer makePathSelection() {
            RenderableLayer layer = new RenderableLayer();
            layer.setName("Path Selection");
            startPin.setValue(AVKey.DISPLAY_NAME, "Start");
            endPin.setValue(AVKey.DISPLAY_NAME, "End");
            layer.addRenderable(startPin);
            layer.addRenderable(endPin);
            return layer;
        }

        // Layer that shows the flight paths of all of the given flights
        private Layer makeFlightPaths() {
            RenderableLayer layer = new RenderableLayer();
            layer.setName("Flight Paths");
            for (Flight f : flights) {
                ArrayList<Position> flightPath = new ArrayList<Position>();
                Path path = new Path();
                path.setPathType("AVKey.GREAT_CIRCLE");
                Position start = Position.fromDegrees(f.getStartLat(), f.getStartLong(), ALTITUDE);
                Position end = Position.fromDegrees(f.getEndLat(), f.getEndLong(), ALTITUDE);
                flightPath.add(start);
                flightPath.add(end);
                path.setPositions(flightPath);
                layer.addRenderable(path);
            }
            layer.setEnabled(false);
            layer.setPickEnabled(false);
            return layer;
        }

        private Layer makeShortestDistance() {
            paths = Dijsktras(startPlane);
            Layer layer = makeShortestPath(paths.get(endPlane), blue, "Shortest Distance" );
            layer.setEnabled(shortestDistanceEnabled);
            return layer;
        }

        private Layer makeFewestTransmissions() {
            paths = DijsktrasUnweighted(startPlane);
            Layer layer = makeShortestPath(paths.get(endPlane), red, "Fewest Transmissions" );
            layer.setEnabled(fewestTransmissionsEnabled);
            return layer;
        }

        // Using Dijkstras creates the shortest path from start pin to end pin
        private Layer makeShortestPath(ArrayList<Integer> path, Material color, String name) {
            RenderableLayer layer = new RenderableLayer();
            layer.setName(name);
            ArrayList<Position> pathPoints = new ArrayList<Position>();
            for (Integer i : path) {
                Position pos = rendered.get(i).getPosition();
                pathPoints.add(new Position(pos.getLatitude(), pos.getLongitude(), ALTITUDE + 100));
            }
            BasicShapeAttributes attrs = new BasicShapeAttributes();
            BasicAirspaceAttributes aAttrs = new BasicAirspaceAttributes();
            attrs.setOutlineMaterial(color);
            aAttrs.setOutlineMaterial(color);
            attrs.setInteriorMaterial(color);
            aAttrs.setInteriorMaterial(color);
            for (int i = 0; i < pathPoints.size(); i++) {
                int current = path.get(i);
                SphereAirspace sphere = new SphereAirspace();
                sphere.setLocation(pathPoints.get(i));
                sphere.setAltitude(ALTITUDE + 100);
                sphere.setRadius(5000);
                sphere.setAttributes(aAttrs);
                sphere.setValue(AVKey.DISPLAY_NAME, String.format("%d", current));
                layer.addRenderable(sphere);
            }
            Path shortestPath = new Path();
            shortestPath.setPathType("AVKey.GREAT_CIRCLE");
            shortestPath.setPositions(pathPoints);
            shortestPath.setAttributes(attrs);
            layer.addRenderable(shortestPath);
            layer.setPickEnabled(false);
            return layer;
        }

        // Layer that creates the spheres  that represent each plane in the simulation
        private Layer makePlanes(long time) {
            RenderableLayer layer = new RenderableLayer();
            layer.setName("Nodes");
            rendered = new ArrayList<Node>();
            AirspaceAttributes nodeHighlight = new BasicAirspaceAttributes();
            nodeHighlight.setInteriorMaterial(Material.PINK);
            int counter = -1;
            for (int i = 0; i < dropOutFlights.size(); i ++) {
                Flight f = dropOutFlights.get(i);
                if (time >= f.getStartTime() && time <= f.getEndTime()) {
                    counter++;
                    SphereAirspace sphere = new SphereAirspace();

                    double amount = (time - f.getStartTime()) / (f.getEndTime() - f.getStartTime());
                    Position currentFlightPos = Position.interpolateGreatCircle(amount,
                        Position.fromDegrees(f.getStartLat(), f.getStartLong(), ALTITUDE),
                        Position.fromDegrees(f.getEndLat(), f.getEndLong(), ALTITUDE));
                    sphere.setLocation(currentFlightPos);
                    sphere.setAltitude(ALTITUDE);
                    sphere.setRadius(5000);
                    sphere.setValue(AVKey.DISPLAY_NAME, String.format("%d", counter));
                    layer.addRenderable(sphere);
                    Node plane = new Node(i, currentFlightPos);
                    rendered.add(plane);
                }
            }
            nodes = rendered.size();
            layer.setEnabled(nodesEnabled);
            layer.setPickEnabled(false);
            return layer;
        }

        // Layer that makes the connections between the nodes based on the COMMS_RADIUS
        private Layer makeConnections() {
            RenderableLayer layer = new RenderableLayer();
            layer.setName("Connections");
            graph = new double[rendered.size() + 1][rendered.size() + 1];
            Path connection;
            connections = 0;
            ArrayList<Position> connectionPoints;
            for (int i = 0; i < rendered.size(); i++) {
                Node a = rendered.get(i);
                for (int j = 0; j < rendered.size(); j++) {
                    Node b = rendered.get(j);
                    double dist = LatLon.linearDistance(a.getPosition(), b.getPosition()).getRadians() * (earth.getRadius() + ALTITUDE);
                    if (dist <= COMMS_RADIUS && i != j) {
                        connections++;
                        connection = new Path();
                        connectionPoints = new ArrayList<Position>();
                        connection.setPathType("AVKey.GREAT_CIRCLE");
                        connectionPoints.add(a.getPosition());
                        connectionPoints.add(b.getPosition());
                        connection.setPositions(connectionPoints);
                        layer.addRenderable(connection);
                        graph[i][j] = dist;
                    }
                    else {
                        graph[i][j] = Double.MAX_VALUE;
                    }
                }
            }
            layer.setEnabled(connectionsEnabled);
            layer.setPickEnabled(false);
            return layer;
        }

        // Layer that creates the circles to represent the ground coverage of the planes
        private Layer makeGroundCoverage() {
            BasicAirspaceAttributes attrs = new BasicAirspaceAttributes();
            attrs.setInteriorMaterial(blue);
            RenderableLayer layer = new RenderableLayer();
            layer.setName("Ground Coverage");
            for (Node f : rendered) {
                CappedCylinder cyl = new CappedCylinder(attrs);
                cyl.setCenter(f.getPosition());
                cyl.setRadii(0, GROUND_COMMS_RADIUS);
                cyl.setAltitudes(0, 10000);
                cyl.setTerrainConforming(false, false);
                layer.addRenderable(cyl);
            }
            layer.setEnabled(groundCoverageEnabled);
            layer.setPickEnabled(false);
            return layer;
        }

        private Layer makeDataBubble() {
            RenderableLayer layer = new RenderableLayer();
            layer.setName("Data Bubble");
            String networkPercent;
            if (rendered.size() != 0) {
                networkPercent = String.format("%.2f", ((float)sizeOfNetwork / (float)rendered.size()) * 100);
            }
            else {
                networkPercent = "undefined";
            }
            String data = String.format("%s\n%d Nodes, %d Connections\nShortest Distance Path (Blue): %.7g km in %d transmissions\nFewest Transmissions Path (Red): %.7g km in %d transmissions\nLargest Connected Network: %d of %d nodes (%s%%)", updatedTime, nodes, connections, weightedDistance, weightedTransmissions, unweightedDistance, unweightedTransmissions, sizeOfNetwork, rendered.size(), networkPercent);
            ScreenAnnotationBalloon dataBalloon = new ScreenAnnotationBalloon(data, new Point(200,1050));
            BasicBalloonAttributes bba = new BasicBalloonAttributes();
            Size balloonSize = new Size(Size.EXPLICIT_DIMENSION, 425, AVKey.PIXELS,Size.EXPLICIT_DIMENSION, 150, AVKey.PIXELS );
            bba.setLeaderShape(AVKey.SHAPE_NONE);
            bba.setSize(balloonSize);
            dataBalloon.setAttributes(bba);
            layer.addRenderable(dataBalloon);
            layer.setPickEnabled(false);
            layer.setEnabled(dataBubbleEnabled);
            return layer;
        }

        // Given input from the JTextField redraws the layers with the time and takes a screenshot with the directory screenshots/*.jpg
        private void updateAirTrafficModel(String input){
            String x = input.replaceAll("[^0-9]", "");
            long num = 0;
            try {
                num = Long.parseLong(x);
                if (num < startTime)
                    num = startTime;
                else if (num > endTime)
                    num = endTime;
                hours = (num / 60) % 24;
                day = (num / 1440) + 1;
                mins = num % 60;
                updatedTime = String.format("2/%d/19   |   %02d : %02d UTC", day, hours, mins);
            }
            catch (NumberFormatException nfe){
                System.err.println("Invalid time value. Defaulting to " + startTime);
                num = startTime;
            }
            finally {
                timeField.setText(String.format("%d", num ));
            }
            Layer layer = layers.getLayerByName("Ground Coverage");
            if (layer != null){
                groundCoverageEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Connections");
            if (layer != null){
                connectionsEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Nodes");
            if (layer != null){
                nodesEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Shortest Distance");
            if (layer != null){
                shortestDistanceEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Fewest Transmissions");
            if (layer != null){
                fewestTransmissionsEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Largest Network");
            if (layer != null){
                largestNetworkEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Largest Network Weighted");
            if (layer != null){
                largestNetworkWeightedEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layer = layers.getLayerByName("Data Bubble");
            if (layer != null){
                dataBubbleEnabled = layer.isEnabled();
                layers.remove(layer);
            }
            layers.add(makePlanes(num));
            layers.add(makeConnections());
            layers.add(makeGroundCoverage());
            startPlane = findNearestPlane(startPin.getPosition());
            endPlane = findNearestPlane(endPin.getPosition());
            layers.add(largestNetwork());
            layers.add(largestNetworkWeighted());
            if (startPlane != -1 && endPlane != -1) {
                layers.add(makeShortestDistance());
                layers.add(makeFewestTransmissions());
            }
            layers.add(makeDataBubble());
        }

        // Repeatedly updates the screen from startTime to endTime allowing for images to be taken for later processing
        private void renderImages() {
            for (int x = startTime; x <= endTime; x++) {
                updateAirTrafficModel(String.format("%d", x));
                screenshot(x);
            }
        }

        // Using Dijsktra's algo to find the shortest path based on distance from start node to end node
        private ArrayList<ArrayList<Integer>> Dijsktras(int startNode ){
            double dist[] = new double[graph.length];
            boolean visited[] = new boolean[graph.length];
            ArrayList<ArrayList<Integer>> paths = new ArrayList<ArrayList<Integer>>();
            int currentNode;
            for(int i = 0; i < graph.length; i++){
                dist[i] = Double.MAX_VALUE;
                visited[i] = false;
                paths.add(new ArrayList<Integer>());
            }
            dist[startNode] = 0;
            ArrayList<Integer> path = new ArrayList<Integer>();
            path.add(startNode);
            paths.set(startNode, path);
            currentNode = getLowestUnvisited(dist, visited);
            do{
                for(int y = 0; y < graph.length - 1; y++){
                    if ((dist[currentNode] + graph[currentNode][y]) < dist[y]) {
                        dist[y] = dist[currentNode] + graph[currentNode][y];
                        ArrayList<Integer> newPath = new ArrayList<Integer>(paths.get(currentNode));
                        newPath.add(y);
                        paths.set(y, newPath);
                    }
                }
                visited[currentNode] = true;
                currentNode = getLowestUnvisited(dist, visited);
            }while(currentNode != -1);
            if(endPlane != -1){
                weightedDistance = dist[endPlane]/1000;
                weightedTransmissions = paths.get(endPlane).size() - 1;
            }
            return paths;
        }

        // Using Dijsktra's algorithim to create a path from start to end node with the least amount of edges
        private ArrayList<ArrayList<Integer>> DijsktrasUnweighted(int startNode){
            paths = new ArrayList<ArrayList<Integer>>();
            double dist[] = new double[graph.length];
            double distM[] = new double[graph.length];
            boolean visited[] = new boolean[graph.length];
            int currentNode;
            for(int i = 0; i < graph.length; i++){
                distM[i] = Double.MAX_VALUE;
                dist[i] = Double.MAX_VALUE;
                visited[i] = false;
                paths.add(new ArrayList<Integer>());
            }
            distM[startNode] = 0;
            dist[startNode] = 0;
            ArrayList<Integer> path = new ArrayList<Integer>();
            path.add(startNode);
            paths.set(startNode, path);
            currentNode = getLowestUnvisited(dist, visited);
            do{
                for(int y = 0; y < graph.length - 1; y++){
                    if ((dist[currentNode]+ 1) < dist[y] && graph[currentNode][y] != Double.MAX_VALUE) {
                        distM[y] = distM[currentNode] + graph[currentNode][y];
                        ArrayList<Integer> newPath = new ArrayList<Integer>(paths.get(currentNode));
                        newPath.add(y);
                        paths.set(y, newPath);
                        dist[y] = dist[currentNode] + 1;
                    }
                }
                visited[currentNode] = true;
                currentNode = getLowestUnvisited(dist, visited);
            }while(currentNode != -1);
            if (endPlane != -1){
                unweightedDistance =  distM[endPlane]/1000;
                unweightedTransmissions = paths.get(endPlane).size() - 1;
            }

            return paths;
        }

        // Return: index of plane with the shortest distance that has yet to be visited
        private int getLowestUnvisited(double[] distance, boolean visit[]){
            double min = Double.MAX_VALUE;
            int mindex = -1;
            for(int i = 0; i < distance.length; i++){
                if(distance[i] < min && !visit[i]){
                    min = distance[i];
                    mindex = i;
                }
            }
            return mindex;
        }

        // Return: finds the nearest index of the plane to the given position
        private int findNearestPlane(Position pos) {
            double min = GROUND_COMMS_RADIUS;
            int mindex = -1;
            for (int n = 0; n < rendered.size(); n++) {
                double dist = LatLon.linearDistance(rendered.get(n).getPosition(), pos).getRadians() * (earth.getRadius() + ALTITUDE);
                if (dist < min) {
                    min = dist;
                    mindex = n;
                }
            }
            return mindex;
        }

        private float connectionPercentage() {
            Random rand = new Random(System.currentTimeMillis());
            double startLat = (rand.nextDouble() * 15.5) + 29.5;
            double startLong = (rand.nextDouble() * 49) - 124;
            startPin.setPosition(Position.fromDegrees(startLat, startLong));
            double endLat = (rand.nextDouble() * 15.5) + 29.5;
            double endLong = (rand.nextDouble() * 49) - 124;
            endPin.setPosition(Position.fromDegrees(endLat, endLong));
            System.out.printf("%f, %f\n", startLat, startLong);
            System.out.printf("%f, %f\n", endLat, endLong);
            int successes = 0;
            for (long i = startTime; i <= endTime; i ++) {
                makePlanes(i);
                startPlane = findNearestPlane(Position.fromDegrees(startLat, startLong));
                endPlane = findNearestPlane(Position.fromDegrees(endLat, endLong));
                makeConnections();
                if (startPlane != -1 && endPlane != -1) {
                    paths = Dijsktras(startPlane);
                    if (paths.get(endPlane).size() != 0) {
                        successes++;
                    }
                }
            }
            float percentage = (float)successes / (float)(endTime - startTime);
            System.out.println(percentage);
            return percentage;
        }

        private float percentageTrials() {
            float runningAverage = 0;
            for (int i = 0; i < 200; i++){
                float adjust = connectionPercentage();
                System.out.print("Trial " + i + ": ");
                runningAverage = ((runningAverage * i) + adjust) / (i+1);
                System.out.println("Running Average: " + runningAverage);
            }
            System.out.println("Final Average: " + runningAverage);
            return(runningAverage);
        }

        private float areaCoverage(int num){
            makePlanes(num);
            Layer groundCoverage = makeGroundCoverage();
            layers.remove(layers.getLayerByName("Ground Coverage"));
            layers.add(groundCoverage);
            screenshot(num);
            ip.maskImage(num);
            return ip.areaCoverage();
        }

        private float coverageTrials() {
            groundCoverageEnabled = true;
            for (Layer i : layers) {
                i.setEnabled(false);
            }
            layers.getLayerByName("Ground Coverage").setEnabled(true);
            layers.getLayerByName("Political Boundaries").setEnabled(true);
            FlatWorldPanel fwp = new FlatWorldPanel(wwd);
            fwp.enableFlatGlobe(true);
            view.setEyePosition(Position.fromDegrees(US_CENTER_LAT, US_CENTER_LONG, 10000000));
            view.setHeading(Angle.fromDegrees(0));
            float runningAverage = 0;
            for (int j = startTime; j < endTime; j++) {
                runningAverage += areaCoverage(j);
                System.out.println("Running Avg: " + (runningAverage / ((j - startTime) + 1)));
            }
            runningAverage = runningAverage  / (endTime - startTime);
            System.out.println("Total Average: " + runningAverage);
            System.out.println("Final Average: " + runningAverage);
            return(runningAverage);
        }

        private void  screenshot(int num){
            synchronized (repaintLock) {
                wwd.redrawNow();
            }
                ScreenShotUtil ssu = new ScreenShotUtil(wwd);
                ssu.actionPerformed(new ActionEvent(wwd, num, "Screenshot"));
        }

        private Layer largestNetwork(){
            boolean[] visited = new boolean[rendered.size()];
            long sizeOfLargest = 0;
            RenderableLayer largestNetworkLayer = new RenderableLayer();
            largestNetworkLayer.setName("Largest Network");
            ArrayList<ArrayList<Integer>> dijkstrasPaths = new ArrayList<ArrayList<Integer>>();
            ArrayList<ArrayList<Integer>> containedPaths = new ArrayList<ArrayList<Integer>>();
            int rootOfLargest = -1;
            for(int i = 0; i < rendered.size(); i++){
                int counter = 0;
                if(sizeOfLargest > rendered.size() - i)
                    break;
                if(visited[i])
                    continue;
                visited[i] = true;
                dijkstrasPaths = DijsktrasUnweighted(i);
                for(int x = 0; x < dijkstrasPaths.size(); x++){
                    if(dijkstrasPaths.get(x).size() != 0){
                        visited[x] = true;
                        counter++;
                    }
                }
                if(counter > sizeOfLargest){
                    sizeOfLargest = counter;
                    rootOfLargest = i;
                }
            }
            sizeOfNetwork = sizeOfLargest;
            if(rootOfLargest != -1)
                dijkstrasPaths = DijsktrasUnweighted(rootOfLargest);
            for(ArrayList<Integer> y : dijkstrasPaths ){
                boolean flag  = false;
                for(ArrayList<Integer> k : containedPaths){
                    if(k.containsAll(y))
                        flag = true;
                }
                if(!flag)
                    containedPaths.add(y);
            }
            BasicShapeAttributes bsa = new BasicShapeAttributes();
            bsa.setOutlineMaterial(Material.GREEN);
            bsa.setInteriorMaterial(Material.GREEN);
            for(ArrayList<Integer> l : containedPaths){
                ArrayList<Position> pos = new ArrayList<Position>();
                for(int i : l) {
                    pos.add(rendered.get(i).getPosition());
                }
                Path path = new Path();
                path.setPositions(pos);
                path.setAttributes(bsa);
                largestNetworkLayer.addRenderable(path);
            }
            largestNetworkLayer.setPickEnabled(false);
            largestNetworkLayer.setEnabled(largestNetworkEnabled);
            return largestNetworkLayer;
        }

        private Layer largestNetworkWeighted(){
            boolean[] visited = new boolean[rendered.size()];
            long sizeOfLargest = 0;
            RenderableLayer largestNetworkLayer = new RenderableLayer();
            largestNetworkLayer.setName("Largest Network Weighted");
            ArrayList<ArrayList<Integer>> dijkstrasPaths = new ArrayList<ArrayList<Integer>>();
            ArrayList<ArrayList<Integer>> containedPaths = new ArrayList<ArrayList<Integer>>();
            int rootOfLargest = -1;
            for(int i = 0; i < rendered.size(); i++){
                int counter = 0;
                if(sizeOfLargest > rendered.size() - i)
                    break;
                if(visited[i])
                    continue;
                visited[i] = true;
                dijkstrasPaths = Dijsktras(i);
                for(int x = 0; x < dijkstrasPaths.size(); x++){
                    if(dijkstrasPaths.get(x).size() != 0){
                        visited[x] = true;
                        counter++;
                    }
                }
                if(counter > sizeOfLargest){
                    sizeOfLargest = counter;
                    rootOfLargest = i;
                }
            }
            sizeOfNetwork = sizeOfLargest;
            if(rootOfLargest != -1)
                dijkstrasPaths = Dijsktras(rootOfLargest);
            for(ArrayList<Integer> y : dijkstrasPaths ){
                boolean flag  = false;
                for(ArrayList<Integer> k : containedPaths){
                    if(k.containsAll(y))
                        flag = true;
                }
                if(!flag)
                    containedPaths.add(y);
            }
            BasicShapeAttributes bsa = new BasicShapeAttributes();
            bsa.setOutlineMaterial(Material.ORANGE);
            bsa.setInteriorMaterial(Material.ORANGE);
            for(ArrayList<Integer> l : containedPaths){
                ArrayList<Position> pos = new ArrayList<Position>();
                for(int i : l) {
                    pos.add(rendered.get(i).getPosition());
                }
                Path path = new Path();
                path.setPositions(pos);
                path.setAttributes(bsa);
                largestNetworkLayer.addRenderable(path);
            }
            largestNetworkLayer.setPickEnabled(false);
            largestNetworkLayer.setEnabled(largestNetworkWeightedEnabled);
            return largestNetworkLayer;
        }
    }
}
