package uk.ac.crick.goehringlab;

import Jama.Matrix;
import Jama.QRDecomposition;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.io.SaveDialog;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.frame.PlugInDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;


public class Autofluorescence_correction extends PlugInDialog implements ActionListener {

    public Autofluorescence_correction() {
        super("Autofluorescence correction");

    }


    public void run(String arg) {
        menuWindowSetup();

    }


    ///////////////////// GLOBALS //////////////////


    // MENU WINDOW

    // Buttons
    private Button menuCalButton;
    private Button menuRunButton;


    // CALIBRATION WINDOW

    // Frame
    private JFrame calFrame;

    // Image names
    private Hashtable<String, Integer> calHashTable = new Hashtable<>();

    // Entry widgets
    private JComboBox<String> calFlChannelBox;
    private JComboBox<String> calAfChannelBox;
    private JComboBox<String> calRedChannelBox;
    private JComboBox<String> calRoiChannelBox;
    private JTextField calGaussianText;

    // Buttons
    private Button calRunButton;
    private Button calResidsButton;
    private Button calTabButton;
    private Button calSaveButton;

    // Images
    private String calFlChannel;
    private String calAfChannel;
    private String calRedChannel;
    private String calRoiChannel;

    // Roi
    private Roi roi;

    // Pixel values
    private double[] calFlGausPixelValsArray;
    private double[] calAfGausPixelValsArray;
    private double[] calRedGausPixelValsArray;
    private double[] calFlPixelValsArray;
    private double[] calAfPixelValsArray;
    private double[] calRedPixelValsArray;

    // Saturate pixel count
    private int calFlSatCount;
    private int calAfSatCount;
    private int calRedSatCount;

    // Coordinates
    private int[] calXCoors;
    private int[] calYCoors;

    // Y predicted
    private double[] calYpred;
    private double[] calResids;

    // Results table
    private ResultsTable calResultsTable;

    // Calibration parameters
    private double cal_m1 = 1.;
    private double cal_m2 = 0.;
    private double cal_c = 0.;
    private double R2;

    // Redidual image
    private ImagePlus calCorrectedImp;

    // RUN WINDOW

    // Image names
    private Hashtable<String, Integer> runHashTable = new Hashtable<>();

    // Entry widgets
    private JComboBox<String> runFlChannelBox;
    private JComboBox<String> runAfChannelBox;
    private JComboBox<String> runRedChannelBox;
    private JTextField runCText;
    private JTextField runM1Text;
    private JTextField runM2Text;

    // Buttons
    private Button runRunButton;

    // Images
    private String runFlChannel;
    private String runAfChannel;
    private String runRedChannel;


    ///////////////////// WINDOWS ////////////////////

    private void menuWindowSetup() {
        // Set up window
        JFrame menuFrame = new JFrame("Autofluorescence correction");
        JPanel panel = new JPanel();

        // Panels

        // Calibrate
        menuCalButton = new Button("Calibrate...");
        menuCalButton.addActionListener(this);
        menuCalButton.setEnabled(true);

        // Run correction
        menuRunButton = new Button("Run correction...");
        menuRunButton.addActionListener(this);
        menuRunButton.setEnabled(true);

        // Add panels
        panel.add(menuCalButton);
        panel.add(menuRunButton);

        // Finish panel
        menuFrame.add(panel);
        menuFrame.pack();
        menuFrame.setLocationRelativeTo(null);
        menuFrame.setVisible(true);
    }


    private void calWindowSetup() {
        // First check that some images are open
        int[] windowList = WindowManager.getIDList();
        if (windowList == null) {
            IJ.showMessage("At least 2 images must be open!");
            return;
        } else if (windowList.length == 1) {
            IJ.showMessage("At least 2 images must be open!");
            return;
        }

        // Set up window
        calFrame = new JFrame("Autofluorescence calibration");
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2, 10, 5));

        // Image titles
        String[] imageTitles = new String[windowList.length];
        String[] imageTitles_with_none = new String[windowList.length + 1];
        imageTitles_with_none[0] = "<None>";
        calHashTable = new Hashtable<>();
        for (int i = 0; i < windowList.length; i++) {
            ImagePlus imp = WindowManager.getImage(windowList[i]);
            imageTitles[i] = imp.getTitle();
            imageTitles_with_none[i + 1] = imp.getTitle();
            calHashTable.put(imageTitles[i], windowList[i]);
        }

        // Panels

        // Fluorophore channel
        JLabel flChannelLabel = new JLabel("GFP channel:", SwingConstants.RIGHT);
        calFlChannelBox = new JComboBox<>(imageTitles);

        // Autofluorescence channel
        JLabel afChannelLabel = new JLabel("AF channel:", SwingConstants.RIGHT);
        calAfChannelBox = new JComboBox<>(imageTitles);

        // Red fluorophore channel (optional)
        JLabel redChannelLabel = new JLabel("RFP channel (optional):", SwingConstants.RIGHT);
        calRedChannelBox = new JComboBox<>(imageTitles_with_none);

        // ROI channel
        JLabel roiChannelLabel = new JLabel("ROI (optional):", SwingConstants.RIGHT);
        String[] roiChannelList = {"<None>", "ROI in GFP channel", "ROI in AF channel", "ROI in RFP channel"};
        calRoiChannelBox = new JComboBox<>(roiChannelList);

        // Gaussian
        JLabel gaussianLabel = new JLabel("Gaussian blur (radius):", SwingConstants.RIGHT);
        calGaussianText = new JTextField("2", 4);

        // Run calibration
        calRunButton = new Button("Run calibration");
        calRunButton.addActionListener(this);
        calRunButton.setEnabled(true);

        // Show residuals
        calResidsButton = new Button("Show residuals");
        calResidsButton.addActionListener(this);
        calResidsButton.setEnabled(false);

        // Export data
        calTabButton = new Button("Results table");
        calTabButton.addActionListener(this);
        calTabButton.setEnabled(false);

        // Save and continue
        calSaveButton = new Button("Save calibration and continue...");
        calSaveButton.addActionListener(this);
        calSaveButton.setEnabled(false);

        // Add panels
        panel.add(flChannelLabel);
        panel.add(calFlChannelBox);
        panel.add(afChannelLabel);
        panel.add(calAfChannelBox);
        panel.add(redChannelLabel);
        panel.add(calRedChannelBox);
        panel.add(roiChannelLabel);
        panel.add(calRoiChannelBox);
        panel.add(gaussianLabel);
        panel.add(calGaussianText);
        panel.add(calRunButton);
        panel.add(calResidsButton);
        panel.add(calTabButton);
        panel.add(calSaveButton);

        // Finish panel
        calFrame.add(panel);
        calFrame.pack();
        calFrame.setLocationRelativeTo(null);
        calFrame.setVisible(true);

    }


    private void runWindowSetup() {
        // First check that some images are open
        int[] windowList = WindowManager.getIDList();
        if (windowList == null) {
            IJ.showMessage("At least 2 images must be open!");
            return;
        } else if (windowList.length == 1) {
            IJ.showMessage("At least 2 images must be open!");
            return;
        }

        // Set up window
        // Frame
        JFrame runFrame = new JFrame("Autofluorescence correction");
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2, 10, 5));

        // Image titles
        String[] imageTitles = new String[windowList.length];
        String[] imageTitles_with_none = new String[windowList.length + 1];
        imageTitles_with_none[0] = "<None>";
        runHashTable = new Hashtable<>();
        for (int i = 0; i < windowList.length; i++) {
            ImagePlus imp = WindowManager.getImage(windowList[i]);
            imageTitles[i] = imp.getTitle();
            imageTitles_with_none[i + 1] = imp.getTitle();
            runHashTable.put(imageTitles[i], windowList[i]);
        }

        // Panels

        // Fluorophore channel
        JLabel flChannelLabel = new JLabel("GFP channel:", SwingConstants.RIGHT);
        runFlChannelBox = new JComboBox<>(imageTitles);

        // Autofluorescence channel
        JLabel afChannelLabel = new JLabel("AF channel:", SwingConstants.RIGHT);
        runAfChannelBox = new JComboBox<>(imageTitles);

        // Red channel
        JLabel redChannelLabel = new JLabel("RFP channel (optional):", SwingConstants.RIGHT);
        runRedChannelBox = new JComboBox<>(imageTitles_with_none);

        // AF calibration
        JLabel cLabel = new JLabel("c:", SwingConstants.RIGHT);
        runCText = new JTextField(String.format("%.3f", cal_c), 4);
        JLabel m1Label = new JLabel("m1:", SwingConstants.RIGHT);
        runM1Text = new JTextField(String.format("%.3f", cal_m1), 4);
        JLabel m2Label = new JLabel("m2:", SwingConstants.RIGHT);
        runM2Text = new JTextField(String.format("%.3f", cal_m2), 4);

        // Run
        runRunButton = new Button("Run correction");
        runRunButton.addActionListener(this);
        runRunButton.setEnabled(true);

        // Add panels
        panel.add(flChannelLabel);
        panel.add(runFlChannelBox);
        panel.add(afChannelLabel);
        panel.add(runAfChannelBox);
        panel.add(redChannelLabel);
        panel.add(runRedChannelBox);
        panel.add(cLabel);
        panel.add(runCText);
        panel.add(m1Label);
        panel.add(runM1Text);
        panel.add(m2Label);
        panel.add(runM2Text);
        panel.add(runRunButton);

        // Finish panel
        runFrame.add(panel);
        runFrame.pack();
        runFrame.setLocationRelativeTo(null);
        runFrame.setVisible(true);

    }


    /////////////////// BUTTON ACTIONS //////////////////

    public void actionPerformed(ActionEvent evt) {
        Button source = (Button) evt.getSource();

        // CALIBRATION

        // Open window
        if (source == menuCalButton)
            calWindowSetup();

        // Run calibration
        if (source == calRunButton) {

            // Deactivate buttons
            calSaveButton.setEnabled(false);
            calTabButton.setEnabled(false);
            calResidsButton.setEnabled(false);

            // Get image names
            calGetImages();

            // Checking image name requirements
            if (Objects.equals(calFlChannel, calAfChannel)) {
                IJ.showMessage("GFP and AF channels must be different");
                return;
            }
            if (Objects.equals(calAfChannel, calRedChannel)) {
                IJ.showMessage("AF and RFP channels must be different");
                return;
            }
            if (Objects.equals(calRedChannel, calFlChannel)) {
                IJ.showMessage("GFP and RFP channels must be different");
                return;
            }

            // Checking images are still open
            int[] windowList = WindowManager.getIDList();
            List<String> imageTitles = new ArrayList<>();
            imageTitles.add("<None>");
            for (int j : windowList) {
                ImagePlus imp = WindowManager.getImage(j);
                imageTitles.add(imp.getTitle());
            }

            if (!imageTitles.contains(calFlChannel)) {
                IJ.showMessage("GFP channel image is not open");
                return;
            }
            if (!imageTitles.contains(calAfChannel)) {
                IJ.showMessage("AF channel image is not open");
                return;
            }
            if (!imageTitles.contains(calRedChannel)) {
                IJ.showMessage("RFP channel image is not open");
                return;
            }


            // Get ROI
            calGetRoi();

            // Checking roi requirements
            if (roi == null) {
                IJ.showMessage("No ROI selected");
                return;
            }

            if (!roi.isArea()) {
                IJ.showMessage("Area selection required");
                return;
            }

            // Get pixel values
            calGetPixels();

            // Warning for saturated pixels
            if (calFlSatCount > 0) {
                IJ.showMessage("WARNING: " + calFlSatCount + " saturated GFP channel pixels");
            }
            if (calAfSatCount > 0) {
                IJ.showMessage("WARNING: " + calFlSatCount + " saturated AF channel pixels");
            }
            if (calRedSatCount > 0) {
                IJ.showMessage("WARNING: " + calRedSatCount + " saturated RFP channel pixels");
            }

            // Run and plot regression
            if (Objects.equals(calRedChannel, "<None>")) {
                calRunRegression2();
                calPlotRegression2();
            } else {
                calRunRegression3();
                calPlotRegression3();
            }

            // Fill results table
            calFillTable();

            // Activate buttons
            calSaveButton.setEnabled(true);
            calTabButton.setEnabled(true);
            calResidsButton.setEnabled(true);
        }

        // Show residuals
        if (source == calResidsButton) {
            if (Objects.equals(calRedChannel, "<None>"))
                calCalcResids2();
            else
                calCalcResids3();

            calShowResids();
        }

        // Export results table
        if (source == calTabButton)
            calShowResTable();

        // Save and continue
        if (source == calSaveButton)
            calRunContinue();

        // RUN

        // Open window
        if (source == menuRunButton)
            runWindowSetup();

        // Run correction
        if (source == runRunButton) {
            // Get image names
            runGetImages();

            // Checking image name requirements
            if (Objects.equals(runFlChannel, runAfChannel)) {
                IJ.showMessage("GFP and AF channels must be different");
                return;
            }
            if (Objects.equals(runAfChannel, runRedChannel)) {
                IJ.showMessage("AF and RFP channels must be different");
                return;
            }
            if (Objects.equals(runRedChannel, runFlChannel)) {
                IJ.showMessage("GFP and RFP channels must be different");
                return;
            }

            // Run correction
            if (Objects.equals(runRedChannel, "<None>"))
                runRunCorrection2();
            else
                runRunCorrection3();

        }

    }


    /////////////// CALIBRATION FUNCTIONS //////////////

    private void calGetImages() {
        calFlChannel = (String) calFlChannelBox.getSelectedItem();
        calAfChannel = (String) calAfChannelBox.getSelectedItem();
        calRedChannel = (String) calRedChannelBox.getSelectedItem();
        calRoiChannel = (String) calRoiChannelBox.getSelectedItem();
    }


    private void calGetRoi() {
        ImagePlus roiImp;
        if (Objects.equals(calRoiChannel, "ROI in GFP channel")) {
            roiImp = WindowManager.getImage(calHashTable.get(calFlChannel));
            roi = roiImp.getRoi();
        } else if (Objects.equals(calRoiChannel, "ROI in AF channel")) {
            roiImp = WindowManager.getImage(calHashTable.get(calAfChannel));
            roi = roiImp.getRoi();
        } else if (Objects.equals(calRoiChannel, "ROI in RFP channel")) {
            if (Objects.equals(calRedChannel, "<None>"))
                roi = null;
            else {
                roiImp = WindowManager.getImage(calHashTable.get(calRedChannel));
                roi = roiImp.getRoi();
            }
        } else {    // If "<None>" -> select whole area
            roiImp = WindowManager.getImage(calHashTable.get(calFlChannel));
            roi = new Roi(0, 0, roiImp.getDimensions()[0], roiImp.getDimensions()[1]);
        }
    }


    private void calGetPixels() {

        // FLOUROPHORE CHANNEL

        // Get image
        ImagePlus flImp = WindowManager.getImage(calHashTable.get(calFlChannel));

        // Duplicate image
        ImagePlus flImp2 = flImp.duplicate();

//        // Convert to 32-bit
//        IJ.run(flImp2, "32-bit", "");

        // Apply gaussian
        IJ.run(flImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());

        // Get pixel values
        calFlSatCount = 0;
        List<Integer> xc = new ArrayList<>();
        List<Integer> yc = new ArrayList<>();
        List<Double> flGausPixelVals = new ArrayList<>();
        List<Double> flPixelVals = new ArrayList<>();
        for (int y = 0; y < flImp.getDimensions()[1]; y++) {
            for (int x = 0; x < flImp.getDimensions()[0]; x++) {
                if (roi.contains(x, y)) {
                    xc.add(x);
                    yc.add(y);
                    flGausPixelVals.add((double) flImp2.getPixel(x, y)[0]);
                    flPixelVals.add((double) flImp.getPixel(x, y)[0]);

                    // Check if saturated
                    if (flImp.getPixel(x, y)[0] >= 65535) {
                        calFlSatCount += 1;
                    }
                }
            }
        }

        // Convert to array
        calXCoors = new int[xc.size()];
        calYCoors = new int[yc.size()];
        calFlGausPixelValsArray = new double[flGausPixelVals.size()];
        calFlPixelValsArray = new double[flPixelVals.size()];
        for (int i = 0; i < xc.size(); i++) calXCoors[i] = xc.get(i);
        for (int i = 0; i < yc.size(); i++) calYCoors[i] = yc.get(i);
        for (int i = 0; i < flGausPixelVals.size(); i++) calFlGausPixelValsArray[i] = flGausPixelVals.get(i);
        for (int i = 0; i < flPixelVals.size(); i++) calFlPixelValsArray[i] = flPixelVals.get(i);

        // Close
        flImp2.close();


        // AF CHANNEL

        // Get image
        ImagePlus afImp = WindowManager.getImage(calHashTable.get(calAfChannel));

        // Duplicate image
        ImagePlus afImp2 = afImp.duplicate();

//        // Convert to 32-bit
//        IJ.run(afImp2, "32-bit", "");

        // Apply gaussian
        IJ.run(afImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());

        // Get pixel values
        List<Double> afGausPixelVals = new ArrayList<>();
        List<Double> afPixelVals = new ArrayList<>();
        for (int y = 0; y < afImp.getDimensions()[1]; y++) {
            for (int x = 0; x < afImp.getDimensions()[0]; x++) {
                if (roi.contains(x, y)) {
                    afGausPixelVals.add((double) afImp2.getPixel(x, y)[0]);
                    afPixelVals.add((double) afImp.getPixel(x, y)[0]);

                    // Check if saturated
                    if (afImp.getPixel(x, y)[0] >= 65535) {
                        calAfSatCount += 1;
                    }
                }
            }
        }

        // Convert to array
        calAfGausPixelValsArray = new double[afGausPixelVals.size()];
        calAfPixelValsArray = new double[afPixelVals.size()];
        for (int i = 0; i < afGausPixelVals.size(); i++) calAfGausPixelValsArray[i] = afGausPixelVals.get(i);
        for (int i = 0; i < afPixelVals.size(); i++) calAfPixelValsArray[i] = afPixelVals.get(i);

        // Close
        afImp2.close();


        // RED CHANNEL

        if (!Objects.equals(calRedChannel, "<None>")) {
            // Get image
            ImagePlus redImp = WindowManager.getImage(calHashTable.get(calRedChannel));

            // Duplicate image
            ImagePlus redImp2 = redImp.duplicate();

//            // Convert to 32-bit
//            IJ.run(redImp2, "32-bit", "");

            // Apply gaussian
            IJ.run(redImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());

            // Get pixel values
            List<Double> redGausPixelVals = new ArrayList<>();
            List<Double> redPixelVals = new ArrayList<>();
            for (int y = 0; y < redImp.getDimensions()[1]; y++) {
                for (int x = 0; x < redImp.getDimensions()[0]; x++) {
                    if (roi.contains(x, y)) {
                        redGausPixelVals.add((double) redImp2.getPixel(x, y)[0]);
                        redPixelVals.add((double) redImp.getPixel(x, y)[0]);

                        // Check if saturated
                        if (redImp.getPixel(x, y)[0] >= 65535) {
                            calRedSatCount += 1;
                        }
                    }
                }
            }

            // Convert to array
            calRedGausPixelValsArray = new double[redGausPixelVals.size()];
            calRedPixelValsArray = new double[redGausPixelVals.size()];
            for (int i = 0; i < redGausPixelVals.size(); i++)
                calRedGausPixelValsArray[i] = redGausPixelVals.get(i);
            for (int i = 0; i < redPixelVals.size(); i++) calRedPixelValsArray[i] = redPixelVals.get(i);

            // Close
            redImp2.close();
        }
    }


    private void calRunRegression2() {
        // REGRESSION

        // Set up matrices for regression
        double[][] x = new double[calAfGausPixelValsArray.length][2];
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {
            x[i][0] = 1.;
            x[i][1] = calAfGausPixelValsArray[i];
        }
        Matrix matrixX = new Matrix(x);
        Matrix matrixY = new Matrix(calFlGausPixelValsArray, calFlGausPixelValsArray.length);

        // Perform regression
        QRDecomposition qr = new QRDecomposition(matrixX);
        Matrix beta = qr.solve(matrixY);

        // Get parameters
        cal_c = beta.get(0, 0);
        cal_m1 = beta.get(1, 0);
        cal_m2 = 0;

        // YPRED
        calYpred = new double[calAfGausPixelValsArray.length];
        calResids = new double[calAfGausPixelValsArray.length];
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {
            calYpred[i] = cal_c + cal_m1 * calAfPixelValsArray[i];
            calResids[i] = calFlGausPixelValsArray[i] - calYpred[i];
        }

        // CALCULATE R SQUARED

        // mean of y[] values
        double sum = 0.0;
        for (double v : calFlGausPixelValsArray) sum += v;
        double mean = sum / calFlGausPixelValsArray.length;

        // total variation to be accounted for
        double sst = 0.0;
        for (double v : calFlGausPixelValsArray) {
            double dev = v - mean;
            sst += dev * dev;
        }

        // variation not accounted for
        Matrix residuals = matrixX.times(beta).minus(matrixY);
        double sse = residuals.norm2() * residuals.norm2();

        // R squared
        R2 = 1.0 - sse / sst;

    }


    private void calRunRegression3() {
        // REGRESSION

        // Set up matrices for regression
        double[][] x = new double[calAfGausPixelValsArray.length][3];
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {
            x[i][0] = 1.;
            x[i][1] = calAfGausPixelValsArray[i];
            x[i][2] = calRedGausPixelValsArray[i];
        }
        Matrix matrixX = new Matrix(x);
        Matrix matrixY = new Matrix(calFlGausPixelValsArray, calFlGausPixelValsArray.length);

        // Perform regression
        QRDecomposition qr = new QRDecomposition(matrixX);
        Matrix beta = qr.solve(matrixY);

        // Get parameters
        cal_c = beta.get(0, 0);
        cal_m1 = beta.get(1, 0);
        cal_m2 = beta.get(2, 0);

        // YPRED
        calYpred = new double[calAfGausPixelValsArray.length];
        calResids = new double[calAfGausPixelValsArray.length];
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {
            calYpred[i] = cal_c + cal_m1 * calAfPixelValsArray[i] + cal_m2 * calRedPixelValsArray[i];
            calResids[i] = calFlGausPixelValsArray[i] - calYpred[i];
        }


        // CALCULATE R SQUARED

        // mean of y[] values
        double sum = 0.0;
        for (double v : calFlGausPixelValsArray) sum += v;
        double mean = sum / calFlGausPixelValsArray.length;

        // total variation to be accounted for
        double sst = 0.0;
        for (double v : calFlGausPixelValsArray) {
            double dev = v - mean;
            sst += dev * dev;
        }

        // variation not accounted for
        Matrix residuals = matrixX.times(beta).minus(matrixY);
        double sse = residuals.norm2() * residuals.norm2();

        // R squared
        R2 = 1.0 - sse / sst;

    }


    private void calPlotRegression2() {

        // ypred
        double[] ypred = new double[calAfGausPixelValsArray.length];
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {
            ypred[i] = cal_c + cal_m1 * calAfGausPixelValsArray[i];
        }

        // Plot points
        Plot plot = new Plot("Linear model", "Linear model: c + m1 * (AF channel)", "GFP channel");
        plot.addPoints(ypred, calFlGausPixelValsArray, Plot.DOT);

        // Plot line
        plot.setColor("red");
        plot.addPoints(ypred, ypred, Plot.LINE);

        // Add equation
        plot.addLabel(0.05, 0.1, "GFP channel = c + m1 * (AF channel)\nc = " + String.format("%.04f", cal_c) + "\nm1 = " + String.format("%.04f", cal_m1) + "\n \nR² = " + String.format("%.04f", R2));

        // Show
        plot.show();

    }


    private void calPlotRegression3() {
        // ypred
        double[] ypred = new double[calAfGausPixelValsArray.length];
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {
            ypred[i] = cal_c + cal_m1 * calAfGausPixelValsArray[i] + cal_m2 * calRedGausPixelValsArray[i];
        }

        // Plot points
        Plot plot = new Plot("Linear model", "Linear model: c + m1 * (AF channel) + m2 * (RFP channel)", "GFP channel");
        plot.addPoints(ypred, calFlGausPixelValsArray, Plot.DOT);

        // Plot line
        plot.setColor("red");
        plot.addPoints(ypred, ypred, Plot.LINE);

        // Add equation
        plot.addLabel(0.05, 0.1, "GFP channel = c + m1 * (AF channel) + m2 * (RFP channel)\nc = " + String.format("%.04f", cal_c) + "\nm1 = " + String.format("%.04f", cal_m1) + "\nm2 = " + String.format("%.04f", cal_m2) + "\n \nR² = " + String.format("%.04f", R2));

        // Show
        plot.show();

    }


    private void calCalcResids2() {
        // Get images
        ImagePlus flImp = WindowManager.getImage(calHashTable.get(calFlChannel));
        ImagePlus afImp = WindowManager.getImage(calHashTable.get(calAfChannel));

        // Duplicate af image
        ImagePlus afImp3 = afImp.duplicate();

        // Calculate autofluorescence
        IJ.run(afImp3, "32-bit", "");
        IJ.run(afImp3, "Multiply...", "value=" + cal_m1 + " stack");
        IJ.run(afImp3, "Add...", "value=" + cal_c + " stack");

        // Perform subtraction -> resids
        ImageCalculator ic = new ImageCalculator();
        calCorrectedImp = ic.run("Subtract create 32-bit stack", flImp, afImp3);

        // Close calculated AF image
        afImp3.close();

    }


    private void calCalcResids3() {
        // Get images
        ImagePlus flImp = WindowManager.getImage(calHashTable.get(calFlChannel));
        ImagePlus afImp = WindowManager.getImage(calHashTable.get(calAfChannel));
        ImagePlus redImp = WindowManager.getImage(calHashTable.get(calRedChannel));

        // Duplicate images
        ImagePlus afImp3 = afImp.duplicate();
        ImagePlus redImp3 = redImp.duplicate();

        // Calculate autofluorescence
        IJ.run(afImp3, "32-bit", "");
        IJ.run(afImp3, "Multiply...", "value=" + cal_m1 + " stack");
        IJ.run(afImp3, "Add...", "value=" + cal_c + " stack");
        IJ.run(redImp3, "32-bit", "");
        IJ.run(redImp3, "Multiply...", "value=" + cal_m2 + " stack");

        // Perform subtraction -> resids
        ImageCalculator ic = new ImageCalculator();
        ImagePlus temp = ic.run("Add create 32-bit stack", afImp3, redImp3);
        calCorrectedImp = ic.run("Subtract create 32-bit stack", flImp, temp);

        // Close images
        afImp3.close();
        redImp3.close();


    }


    private void calShowResids() {
        // Duplicate residuals
        ImagePlus calCorrectedImp2 = calCorrectedImp.duplicate();

        // Show
        calCorrectedImp2.show();

        // Set title
        calCorrectedImp2.setTitle("Residuals");
    }


    private void calRunContinue() {
        calFrame.dispatchEvent(new WindowEvent(calFrame, WindowEvent.WINDOW_CLOSING));
    }


    private void calFillTable() {
        calResultsTable = new ResultsTable();
        for (int i = 0; i < calAfGausPixelValsArray.length; i++) {

            // Increment counter
            calResultsTable.incrementCounter();

            // Add coordinates
            calResultsTable.addValue("x_position", calXCoors[i]);
            calResultsTable.addValue("y_position", calYCoors[i]);

            // Add pixel values
            calResultsTable.addValue("gfp_raw", calFlPixelValsArray[i]);
            calResultsTable.addValue("GFP_GAUS", calFlGausPixelValsArray[i]);
            calResultsTable.addValue("af_raw", calAfPixelValsArray[i]);
            calResultsTable.addValue("af_gaus", calAfGausPixelValsArray[i]);
            if (!Objects.equals(calRedChannel, "<None>")) {
                calResultsTable.addValue("rfp_raw", calRedPixelValsArray[i]);
                calResultsTable.addValue("rfp_gaus", calRedGausPixelValsArray[i]);
            }

            // Add predicted
            calResultsTable.addValue("LINEAR_MODEL", calYpred[i]);

            // Add residuals
            calResultsTable.addValue("RESIDUALS", calResids[i]);
        }
    }

    private void calShowResTable() {
//        SaveDialog sd = new SaveDialog("Export data as csv", "af_calibration_data", ".csv");
//        calResultsTable.save(sd.getDirectory() + sd.getFileName());
        calResultsTable.show("Results table");
    }

    /////////////// CORRECTION FUNCTIONS ///////////////


    private void runGetImages() {
        runFlChannel = (String) runFlChannelBox.getSelectedItem();
        runAfChannel = (String) runAfChannelBox.getSelectedItem();
        runRedChannel = (String) runRedChannelBox.getSelectedItem();

    }

    private void runRunCorrection2() {
        // Get images
        ImagePlus flImp = WindowManager.getImage(runHashTable.get(runFlChannel));
        ImagePlus afImp = WindowManager.getImage(runHashTable.get(runAfChannel));

        // Duplicate af image
        ImagePlus afImp3 = afImp.duplicate();

        // Calculate autofluorescence
        IJ.run(afImp3, "32-bit", "");
        IJ.run(afImp3, "Multiply...", "value=" + runM1Text.getText() + " stack");
        IJ.run(afImp3, "Add...", "value=" + runCText.getText() + " stack");

        // Perform subtraction
        ImageCalculator ic = new ImageCalculator();
        ImagePlus correctedImp = ic.run("Subtract create 32-bit stack", flImp, afImp3);
        correctedImp.show();

        // Close calculated AF image
        afImp3.close();

    }

    private void runRunCorrection3() {
        // Get images
        ImagePlus flImp = WindowManager.getImage(runHashTable.get(runFlChannel));
        ImagePlus afImp = WindowManager.getImage(runHashTable.get(runAfChannel));
        ImagePlus redImp = WindowManager.getImage(runHashTable.get(runRedChannel));

        // Duplicate images
        ImagePlus afImp3 = afImp.duplicate();
        ImagePlus redImp3 = redImp.duplicate();

        // Calculate autofluorescence
        IJ.run(afImp3, "32-bit", "");
        IJ.run(afImp3, "Multiply...", "value=" + runM1Text.getText() + " stack");
        IJ.run(afImp3, "Add...", "value=" + runCText.getText() + " stack");
        IJ.run(redImp3, "32-bit", "");
        IJ.run(redImp3, "Multiply...", "value=" + runM2Text.getText() + " stack");

        // Perform subtraction
        ImageCalculator ic = new ImageCalculator();
        ImagePlus temp = ic.run("Add create 32-bit stack", afImp3, redImp3);
        ImagePlus correctedImp = ic.run("Subtract create 32-bit stack", flImp, temp);
        correctedImp.show();

        // Close images
        afImp3.close();
        redImp3.close();

    }

}


/*

Calibration fails if images are 32-bit - investigate
Keep image list updated (if possible)
Prevent multiple calibration windows from being open at the same time
Show residuals image (AF + background) for correction (multi-channel with GFP signal)
Ability to run with macros

 */



