/*
 * Copyright (C) 2019 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

package gov.nasa.worldwindx.applications;

import gov.nasa.worldwind.geom.Position;

public class Node
{
    private int id;
    private Position position;

    public Node(int id, Position position)
    {
        this.id = id;
        this.position = position;
    }
    
    int getId() {return this.id;}
    Position getPosition() {return this.position;}
}
