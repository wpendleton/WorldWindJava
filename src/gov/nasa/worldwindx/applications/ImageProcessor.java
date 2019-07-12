/*
 * Copyright (C) 2019 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */

package gov.nasa.worldwindx.applications;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.*;

class ImageProcessor
{
    private final Object fileLock = new Object();
    public void maskImage(long time)
    {
        try {
            System.out.println("The time is: " + time);
            BufferedImage mask = ImageIO.read(new File("data/mask.png"));
            File file = new File(String.format("screenshots/%d.png",time));
            BufferedImage screenshot = ImageIO.read(file);
            Image scaled = screenshot.getScaledInstance(1596, 1001, Image.SCALE_DEFAULT);
            BufferedImage composite = new BufferedImage(1596,1001, BufferedImage.TYPE_INT_ARGB);
            Graphics g = composite.getGraphics();
            g.drawImage(scaled, 0,0 , null);
            g.drawImage(mask, 0,0 , null);
            composite = composite.getSubimage(228, 290, 1256, 535);
            synchronized (fileLock){
                ImageIO.write(composite, "PNG", new File("data/processed.png"));
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public float areaCoverage(){
        int total = 0;
        int covered = 0;
        try{
            BufferedImage processed;
            synchronized (fileLock){
                processed = ImageIO.read(new File("data/processed.png"));
            }

           for(int i = 0; i < processed.getWidth(); i++){
               for(int j  = 0; j < processed.getHeight(); j++){
                   int pixel = processed.getRGB(i,j);
                   int red = (pixel & 0x00ff0000) >> 16;
                   int green = (pixel & 0x0000ff00) >> 8;
                   int blue = pixel & 0x000000ff;
                   if(red == 0 && green == 0 && blue == 0){
                       total++;
                   }
                   if(blue == 255){
                       total++;
                       covered++;
                   }
               }
           }
       }
       catch(IOException e){
           e.printStackTrace();
       }
        if(total != 0) {
            System.out.println("Total: " + total + " Coverage: " + covered);
            return ((float) covered /(float)  total);
        }
        return -1;
    }
}
