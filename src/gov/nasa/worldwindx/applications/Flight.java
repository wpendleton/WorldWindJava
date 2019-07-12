package gov.nasa.worldwindx.applications;

public class Flight
{
    private double startLat;
    private double startLong;
    private double startTime;
    private double endLat;
    private double endLong;
    private double endTime;

    public Flight(double startLat, double startLong, double startTime, double endLat, double endLong, double endTime)
    {
        this.startLat = startLat;
        this.startLong = startLong;
        this.startTime = startTime;
        this.endLat = endLat;
        this.endLong = endLong;
        this.endTime = endTime;
    }

    public double getStartLat() {return this.startLat;}
    public double getStartLong() {return this.startLong;}
    public double getStartTime() {return this.startTime;}
    public double getEndLat() {return this.endLat;}
    public double getEndLong() {return this.endLong;}
    public double getEndTime() {return this.endTime;}

    public String toString()
    {
        return String.format("%f, %f, %f, %f, %f, %f", this.startLat, this.startLong, this.startTime, this.endLat, this.endLong, this.endTime);
    }
}