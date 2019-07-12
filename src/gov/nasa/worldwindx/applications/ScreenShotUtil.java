package gov.nasa.worldwindx.applications;

import com.jogamp.opengl.util.awt.*;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.event.*;
import gov.nasa.worldwind.util.WWIO;

import javax.imageio.ImageIO;
import javax.media.opengl.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;

public class ScreenShotUtil extends AbstractAction implements RenderingListener
{
    WorldWindow wwd;
    private File snapFile;
    JFileChooser fileChooser;

    public ScreenShotUtil(WorldWindow wwd)
    {
        super("Screen Shot");
        this.wwd = wwd;
        this.fileChooser = new JFileChooser();
    }

    public void actionPerformed(ActionEvent event)
    {
        this.snapFile = this.chooseFile(event.getID());
    }

    private File chooseFile(int num)
    {
        this.wwd.removeRenderingListener(this);
        this.wwd.addRenderingListener(this);
        return new File(String.format("screenshots/%d.png", num));
    }

    public void stageChanged(RenderingEvent event)
    {
        if (event.getStage().equals(RenderingEvent.AFTER_BUFFER_SWAP) && this.snapFile != null)
        {
            try
            {
                GLAutoDrawable glad = (GLAutoDrawable) event.getSource();
                AWTGLReadBufferUtil glReadBufferUtil = new AWTGLReadBufferUtil(glad.getGLProfile(), false);
                BufferedImage image = glReadBufferUtil.readPixelsToBufferedImage(glad.getGL(), true);
                String suffix = WWIO.getSuffix(this.snapFile.getPath());
                ImageIO.write(image, suffix, this.snapFile);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            finally
            {
                this.snapFile = null;
                this.wwd.removeRenderingListener(this);
            }
        }
    }

    private String composeSuggestedName()
    {
        String baseName = "WWJSnapShot";
        String suffix = ".png";

        File currentDirectory = this.fileChooser.getCurrentDirectory();

        File candidate = new File(currentDirectory.getPath() + File.separatorChar + baseName + suffix);
        for (int i = 1; candidate.exists(); i++)
        {
            String sequence = String.format("%03d", i);
            candidate = new File(currentDirectory.getPath() + File.separatorChar + baseName + sequence + suffix);
        }

        return candidate.getPath();
    }
}
