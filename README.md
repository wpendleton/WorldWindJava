# WorldWindFlightModel
Airborne NC3 Communication System Flight Model Application in NASA WorldWind  

# Setup
Clone this repository or download it as a .zip and extract it

# Running
Run FlightModel.java with program arguments [startTime, endTime, dropOutPercent] (Use [1440, 2880, 1] if unsure)  

startTime is the number of minutes after 0:00 UTC Feb 1st 2019 (Different if using a custom dataset) to begin loading data from data.csv (64 Bit Integer)

endTime is the number of minutes after 0:00 UTC Feb 1st 2019 (Different if using a custom dataset) to stop loading data from data.csv (64 Bit Integer)

dropOutPercent is the percent chance to load a flight from data.csv (Floating point 0 - 1) (1 will load all data, 0 will load no data, 0.5 will randomly select half the data points to load)  

# Usage
Enter any integer in the text field in the range from startTime to endTime program arguments and press the "Update" button to render the data to screen at that time  

Select the layers you want to be visible and press the "Render" button to generate a .png image of each frame from startTime to endTime in the screenshots folder. These can be stitched into a video file.  

Click the "Percentage" button to run a connection reliability trial. This will pick 200 random pairs of points and output what percentage of frames have a valid shortest path between those two points, as well as a running average.  

Click the "Coverage" button to run a ground coverage trial, this will iterate over the entire dataset and calculate the ground coverage of the nodes.   

# Layers
Flight Paths - Renders all paths taken by planes in the loaded dataset  

Path Selection - Contains two pins used for selecting the start and end positions for the shortest path layers  

Nodes - Renders all planes in the air at the given time  

Connections - Renders connections between any nodes within ~240 linear miles of eachother  

Ground Coverage - Renders Cylinders onto the earth around each plane depicting what area on the ground each node could reach with its communications  

Largest Network - Finds the largest connected component of the network and draws a fewest transmissions path from one node in that component to all other nodes in that component  

Largest Network Weighted - Finds the largest connected component of the network and draws a shortest distance path from one node in that component to all other nodes in that component  

Shortest Distance - Draws a shortest distance path from the node nearest the "Start" pin to the node nearest the "End" pin on the Path Selection Layer  

Fewest Transmissions - Draws a Fewest Transmissions path from the node nearest the "Start" pin to the node nearest the "End" pin on the Path Selection Layer  

Data Bubble - Draws a Screen Annotation with data about the current render. This includes: time and date of selected time, number of nodes and connections drawn, length of shortest paths, and coverage of largest networks  

# Data
data.csv contains flight data from the Bureau of Transportation Statistics' On-Time Reporting Database.

This application expects the first row of the csv to be a header.

This application expects the following columns in this order in the csv:

ORIGIN_LAT - The latitude of the airport the plane takes off from.

ORIGIN_LONG - The longitude of the airport the plane takes off from.

DEP_DELAY_DAY - The number of minutes past the beginning of your dataset the plane takes off (One day is 1440 minutes)

DEST_LAT - The latitude of the airport the plane lands at.

DEST_LONG - The longitude of the airport the plane lands at.

ARR_DELAY_DAY - The number of minutes past the beginning of your dataset the plane lands (One day is 1440 minutes)

If you wish to use your own dataset, the database is available for download at https://www.transtats.bts.gov/DL_SelectFields.asp

Airport Longitude and Latitude data are available at https://raw.githubusercontent.com/jpatokal/openflights/master/data/airports.dat

In order to create a dataset download the following columns: FlightDate, Origin, Dest, WheelsOff, WheelsOn

You can use the Airport.dat from OpenFlights to lookup the Origin and Destination Longitude, Latitudes, and Time Zones.

Use the time zones, the Flight Date, and the WheelsOff and WheelsOn Times to calculate the delay times.

Example Flight 05/05/2018, JFK, LAX, 1247, 1556 (Using data starting from 5/01/2018)

Days past 1: 4  
Days converted to minutes: 4 * 1440 = 5760

JFK time zone: -5  
Departure time adjusted: (1247 - (-5 * 100)) = 1747  
Departure time to minutes: ((1747 / 100) * 60) + (1747 % 100) = 1067  
Departure time day added: 1067 + 5760 = 6827  
DEP_DELAY_DAY: 6827  

LAX time zone: -8  
Arrival time adjusted: (1556 - (-8 * 100)) = 2356  
Arrival time to minutes: ((2356 / 100) * 60) + (2356 % 100) = 1436  
Arrival time day added: 1436 + 5760 = 7196  
ARR_DELAY_DAY: 7196

ORIGIN_LAT: 40.63980103  
ORIGIN_LONG: -73.77890015  
DEST_LAT: 33.94250107  
DEST_LONG: -118.4079971  
