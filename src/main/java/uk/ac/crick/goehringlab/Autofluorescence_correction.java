package uk.ac.crick.goehringlab;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.ChannelSplitter;
import ij.plugin.ImageCalculator;
import ij.plugin.frame.PlugInDialog;

import Jama.Matrix;
import Jama.QRDecomposition;


public class Autofluorescence_correction extends PlugInDialog implements ActionListener {

    public Autofluorescence_correction() {
        super("Autofluorescence correction");
    }


    public void run(String arg) {
        menuWindowSetup();
    }


    ///////////////////// GLOBALS //////////////////


    // MENU WINDOW

    // Frame
    private JFrame menuFrame;

    // Buttons
    private Button menuCalButton;
    private Button menuRunButton;


    // CALIBRATION WINDOW

    // Frame
    private JFrame calFrame;

    // Image names
    private Hashtable<String, Integer> calHashTable = new Hashtable<>();
    private Hashtable<String, Integer> calChannelsHashTable = new Hashtable<>();

    // Entry widgets
    private JCheckBox[] calImageCheckboxes;
    private JComboBox<String> calFlChannelBox;
    private JComboBox<String> calAfChannelBox;
    private JComboBox<String> calRedChannelBox;
    private JTextField calGaussianText;

    // Buttons
    private Button calRunButton;
    private Button calResidsButton;
    private Button calTabButton;
    private Button calSaveButton;

    // Images
    private List<String> calSelectedImageTitles;

    // Channels
    private String calFlChannel;
    private String calAfChannel;
    private String calRedChannel;

    // Results table
    private ResultsTable calResultsTable;

    // Calibration parameters
    private double cal_m1 = 1.;
    private double cal_m2 = 0.;
    private double cal_c = 0.;
    private double R2;


    // RUN WINDOW

    // Frame
    private JFrame runFrame;

    // Image names
    private Hashtable<String, Integer> runHashTable = new Hashtable<>();
    private Hashtable<String, Integer> runChannelsHashTable = new Hashtable<>();

    // Entry widgets
    private JComboBox<String> runImageBox;
    private JComboBox<String> runFlChannelBox;
    private JComboBox<String> runAfChannelBox;
    private JComboBox<String> runRedChannelBox;
    private JTextField runCText;
    private JTextField runM1Text;
    private JTextField runM2Text;

    // Buttons
    private Button runRunButton;

    // Image
    private String runImageName;

    // Channels
    private String runFlChannel;
    private String runAfChannel;
    private String runRedChannel;


    ///////////////////// WINDOWS ////////////////////

    private void menuWindowSetup() {
        // Set up window
        menuFrame = new JFrame("Autofluorescence correction");
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

        // Check that some images are open
        int[] windowList = WindowManager.getIDList();
        if (windowList == null) {
            IJ.showMessage("No images open!");
            return;
        }

        // Set up window
        calFrame = new JFrame("Autofluorescence calibration");
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2, 10, 5));

        // Image titles
        String[] imageTitles = new String[windowList.length];
        calHashTable = new Hashtable<>();
        int maxChannels = 0;
        for (int i = 0; i < windowList.length; i++) {
            ImagePlus imp = WindowManager.getImage(windowList[i]);
            imageTitles[i] = imp.getTitle();
            maxChannels = Math.max(imp.getDimensions()[2], maxChannels);
            calHashTable.put(imageTitles[i], windowList[i]);
        }

        // Channels list
        String[] channels = new String[maxChannels];
        String[] channels_with_none = new String[maxChannels + 1];
        channels_with_none[0] = "<None>";
        calChannelsHashTable = new Hashtable<>();
        for (int i = 0; i < maxChannels; i++) {
            channels[i] = "Channel " + (i + 1);
            channels_with_none[i + 1] = "Channel " + (i + 1);
            calChannelsHashTable.put(channels[i], i);
        }


        // Panels

        // Images
        JLabel imageLabel = new JLabel("Select image(s):", SwingConstants.RIGHT);
        calImageCheckboxes = new JCheckBox[imageTitles.length];
        for (int i = 0; i < imageTitles.length; i++) {
            calImageCheckboxes[i] = new JCheckBox(imageTitles[i]);
            calImageCheckboxes[i].setSelected(true);
        }

        // Fluorophore channel
        JLabel flChannelLabel = new JLabel("GFP channel:", SwingConstants.RIGHT);
        calFlChannelBox = new JComboBox<>(channels);

        // Autofluorescence channel
        JLabel afChannelLabel = new JLabel("AF channel:", SwingConstants.RIGHT);
        calAfChannelBox = new JComboBox<>(channels);

        // Red fluorophore channel (optional)
        JLabel redChannelLabel = new JLabel("RFP channel (optional):", SwingConstants.RIGHT);
        calRedChannelBox = new JComboBox<>(channels_with_none);

        // ROI
        JLabel roiLabel = new JLabel("ROI (optional):", SwingConstants.RIGHT);
        JLabel roiLabel2 = new JLabel("Specify ROI on image(s)");
        Font f = roiLabel2.getFont();
        roiLabel2.setFont(f.deriveFont(f.getStyle() | Font.ITALIC));

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
        calTabButton = new Button("Export pixel data");
        calTabButton.addActionListener(this);
        calTabButton.setEnabled(false);

        // Save and continue
        calSaveButton = new Button("Save calibration and continue...");
        calSaveButton.addActionListener(this);
        calSaveButton.setEnabled(false);

        // Add panels
        panel.add(imageLabel);
        for (int i = 0; i < calImageCheckboxes.length; i++) {
            panel.add(calImageCheckboxes[i]);
            if (i < (calImageCheckboxes.length - 1)) {
                panel.add(new JLabel(""));
            }
        }
        panel.add(flChannelLabel);
        panel.add(calFlChannelBox);
        panel.add(afChannelLabel);
        panel.add(calAfChannelBox);
        panel.add(redChannelLabel);
        panel.add(calRedChannelBox);
        panel.add(roiLabel);
        panel.add(roiLabel2);
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
            IJ.showMessage("No images open!");
            return;
        }

        // Set up window
        // Frame
        runFrame = new JFrame("Autofluorescence correction");
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0, 2, 10, 5));

        // Image titles
        String[] imageTitles = new String[windowList.length];
        runHashTable = new Hashtable<>();
        int maxChannels = 0;
        for (int i = 0; i < windowList.length; i++) {
            ImagePlus imp = WindowManager.getImage(windowList[i]);
            imageTitles[i] = imp.getTitle();
            maxChannels = Math.max(imp.getDimensions()[2], maxChannels);
            runHashTable.put(imageTitles[i], windowList[i]);
        }

        // Channels list
        String[] channels = new String[maxChannels];
        String[] channels_with_none = new String[maxChannels + 1];
        channels_with_none[0] = "<None>";
        runChannelsHashTable = new Hashtable<>();
        for (int i = 0; i < maxChannels; i++) {
            channels[i] = "Channel " + (i + 1);
            channels_with_none[i + 1] = "Channel " + (i + 1);
            runChannelsHashTable.put(channels[i], i);
        }

        // Panels

        // Image
        JLabel imageLabel = new JLabel("Image:", SwingConstants.RIGHT);
        runImageBox = new JComboBox<>(imageTitles);

        // Fluorophore channel
        JLabel flChannelLabel = new JLabel("GFP channel:", SwingConstants.RIGHT);
        runFlChannelBox = new JComboBox<>(channels);

        // Autofluorescence channel
        JLabel afChannelLabel = new JLabel("AF channel:", SwingConstants.RIGHT);
        runAfChannelBox = new JComboBox<>(channels);

        // Red fluorophore channel (optional)
        JLabel redChannelLabel = new JLabel("RFP channel (optional):", SwingConstants.RIGHT);
        runRedChannelBox = new JComboBox<>(channels_with_none);

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
        panel.add(imageLabel);
        panel.add(runImageBox);
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
            // Check if window is already open
            if (calFrame == null)
                calWindowSetup();
            else if (!calFrame.isVisible())
                calWindowSetup();
            else
                calFrame.toFront();

        // Run calibration
        if (source == calRunButton) {
            calRun();
        }

        // Show residuals
        if (source == calResidsButton) {
            if (Objects.equals(calRedChannel, "<None>"))
                for (String title : calSelectedImageTitles) {
                    ImagePlus imp = WindowManager.getImage(calHashTable.get(title));
                    calShowResids2(imp, title);
                }
            else
                for (String title : calSelectedImageTitles) {
                    ImagePlus imp = WindowManager.getImage(calHashTable.get(title));
                    calShowResids3(imp, title);
                }
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

            // Check if window is already open
            if (runFrame == null)
                runWindowSetup();
            else if (!runFrame.isVisible())
                runWindowSetup();
            else
                runFrame.toFront();

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

            // Checking image is still open
            int[] windowList = WindowManager.getIDList();
            List<String> imageTitles = new ArrayList<>();
            for (int j : windowList) {
                ImagePlus imp = WindowManager.getImage(j);
                imageTitles.add(imp.getTitle());
            }

            if (!imageTitles.contains(runImageName)) {
                IJ.showMessage("Image is not open!");
                return;
            }

            // Checking image bit depth
            ImagePlus imp = WindowManager.getImage(runHashTable.get(runImageName));
            int bitDepth = imp.getBitDepth();
            if (bitDepth != 16) {
                IJ.showMessage("16-bit image required");
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

    private void calRun() {

        // Deactivate buttons
        calSaveButton.setEnabled(false);
        calTabButton.setEnabled(false);
        calResidsButton.setEnabled(false);

        // Get channels
        calGetChannels();

        // Checking channel requirements
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

        // List of all images
        int[] windowList = WindowManager.getIDList();
        List<String> imageTitles = new ArrayList<>();
        for (int j : windowList) {
            ImagePlus imp = WindowManager.getImage(j);
            imageTitles.add(imp.getTitle());
        }

        // List of selected images
        calSelectedImageTitles = new ArrayList<>();
        for (int i = 0; i < imageTitles.size(); i++) {
            if (calImageCheckboxes[i].isSelected()) {
                calSelectedImageTitles.add(imageTitles.get(i));
            }
        }

        // Error if no images are selected
        if (calSelectedImageTitles.size() == 0) {
            IJ.showMessage("No images selected!");
            return;
        }

        // For each image
        calEmbryoData[] allEmbryoData = new calEmbryoData[calSelectedImageTitles.size()];
        for (int i = 0; i < calSelectedImageTitles.size(); i++) {
            String imageName = calSelectedImageTitles.get(i);

            // Checking image is still open
            if (!imageTitles.contains(imageName)) {
                IJ.showMessage("Image is not open!");
                return;
            }

            // Open image
            ImagePlus imp = WindowManager.getImage(calHashTable.get(imageName));

            // Checking image bit depth
            int bitDepth = imp.getBitDepth();
            if (bitDepth != 16) {
                IJ.showMessage("16-bit image required");
                return;
            }

            // Get ROI
            Roi roi = calGetRoi(imp);

            // Checking roi requirements
            if (!roi.isArea()) {
                IJ.showMessage("Area selection required");
                return;
            }

            // Get embryo data
            calEmbryoData data;
            if (Objects.equals(calRedChannel, "<None>")) {
                data = calGetPixels2(imp, roi);
            } else {
                data = calGetPixels3(imp, roi);
            }

            // Warning for saturated pixels
            if (data.flSatCount > 0) {
                IJ.showMessage("WARNING: " + data.flSatCount + " saturated GFP channel pixels");
            }
            if (data.afSatCount > 0) {
                IJ.showMessage("WARNING: " + data.afSatCount + " saturated AF channel pixels");
            }
            if (data.redSatCount > 0) {
                IJ.showMessage("WARNING: " + data.redSatCount + " saturated RFP channel pixels");
            }

            // Save data
            allEmbryoData[i] = data;

        }

        // Total number of pixels
        int n = 0;
        for (calEmbryoData i : allEmbryoData) n += i.n;

        // Pool pixels
        double[] flVals = new double[n];
        double[] afVals = new double[n];
        double[] redVals = new double[n];
        int k = 0;
        if (Objects.equals(calRedChannel, "<None>")) {
            for (calEmbryoData i : allEmbryoData) {
                for (int j = 0; j < i.n; j++) {
                    flVals[k] = i.flGausPixelVals[j];
                    afVals[k] = i.afGausPixelVals[j];
                    k += 1;
                }
            }
        } else {
            for (calEmbryoData i : allEmbryoData) {
                for (int j = 0; j < i.n; j++) {
                    flVals[k] = i.flGausPixelVals[j];
                    afVals[k] = i.afGausPixelVals[j];
                    redVals[k] = i.redGausPixelVals[j];
                    k += 1;
                }
            }
        }

        // Run and plot regression
        if (Objects.equals(calRedChannel, "<None>")) {
            calRunRegression2(flVals, afVals);
            calPlotRegression2(allEmbryoData);
        } else {
            calRunRegression3(flVals, afVals, redVals);
            calPlotRegression3(allEmbryoData);
        }

        // Create results table
        calFillTable(allEmbryoData);

        // Activate buttons
        calSaveButton.setEnabled(true);
        calTabButton.setEnabled(true);
        calResidsButton.setEnabled(true);
    }


    private void calGetChannels() {
        calFlChannel = (String) calFlChannelBox.getSelectedItem();
        calAfChannel = (String) calAfChannelBox.getSelectedItem();
        calRedChannel = (String) calRedChannelBox.getSelectedItem();
    }


    private Roi calGetRoi(ImagePlus imp) {
        // Get ROI
        Roi roi = imp.getRoi();

        // If no ROI, select whole area
        if (roi == null)
            roi = new Roi(0, 0, imp.getDimensions()[0], imp.getDimensions()[1]);

        return roi;
    }


    private static class calEmbryoData {
        int n;

        int flSatCount;
        int afSatCount;
        int redSatCount;

        int[] xc;
        int[] yc;

        double[] flGausPixelVals;
        double[] flPixelVals;
        double[] afGausPixelVals;
        double[] afPixelVals;
        double[] redGausPixelVals;
        double[] redPixelVals;

        // Three channel method
        public calEmbryoData(int n, int flSatCount, int afSatCount, int redSatCount, List<Integer> xc, List<Integer> yc,
                             List<Double> flPixelVals, List<Double> flGausPixelVals,
                             List<Double> afPixelVals, List<Double> afGausPixelVals,
                             List<Double> redPixelVals, List<Double> redGausPixelVals) {

            this.n = n;

            this.flSatCount = flSatCount;
            this.afSatCount = afSatCount;
            this.redSatCount = redSatCount;

            this.xc = new int[xc.size()];
            this.yc = new int[yc.size()];
            for (int i = 0; i < xc.size(); i++) this.xc[i] = xc.get(i);
            for (int i = 0; i < yc.size(); i++) this.yc[i] = yc.get(i);

            this.flPixelVals = new double[flPixelVals.size()];
            this.flGausPixelVals = new double[flPixelVals.size()];
            this.afPixelVals = new double[flPixelVals.size()];
            this.afGausPixelVals = new double[flPixelVals.size()];
            this.redPixelVals = new double[flPixelVals.size()];
            this.redGausPixelVals = new double[flPixelVals.size()];
            for (int i = 0; i < flPixelVals.size(); i++) this.flPixelVals[i] = flPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.flGausPixelVals[i] = flGausPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.afPixelVals[i] = afPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.afGausPixelVals[i] = afGausPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.redPixelVals[i] = redPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.redGausPixelVals[i] = redGausPixelVals.get(i);

        }

        // Two channel method
        public calEmbryoData(int n, int flSatCount, int afSatCount, List<Integer> xc, List<Integer> yc,
                             List<Double> flPixelVals, List<Double> flGausPixelVals,
                             List<Double> afPixelVals, List<Double> afGausPixelVals) {

            this.n = n;

            this.flSatCount = flSatCount;
            this.afSatCount = afSatCount;

            this.xc = new int[xc.size()];
            this.yc = new int[yc.size()];
            for (int i = 0; i < xc.size(); i++) this.xc[i] = xc.get(i);
            for (int i = 0; i < yc.size(); i++) this.yc[i] = yc.get(i);

            this.flPixelVals = new double[flPixelVals.size()];
            this.flGausPixelVals = new double[flPixelVals.size()];
            this.afPixelVals = new double[flPixelVals.size()];
            this.afGausPixelVals = new double[flPixelVals.size()];
            for (int i = 0; i < flPixelVals.size(); i++) this.flPixelVals[i] = flPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.flGausPixelVals[i] = flGausPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.afPixelVals[i] = afPixelVals.get(i);
            for (int i = 0; i < flPixelVals.size(); i++) this.afGausPixelVals[i] = afGausPixelVals.get(i);

        }
    }


    private calEmbryoData calGetPixels2(ImagePlus imp, Roi roi) {

        // Get channels
        ImagePlus[] channels = ChannelSplitter.split(imp);
        ImagePlus flImp = channels[calChannelsHashTable.get(calFlChannel)];
        ImagePlus afImp = channels[calChannelsHashTable.get(calAfChannel)];

        // Process channels
        ImagePlus flImp2 = flImp.duplicate();
        IJ.run(flImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());
        ImagePlus afImp2 = afImp.duplicate();
        IJ.run(afImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());

        // Set up results containers
        int n = 0;
        int flSatCount = 0;
        int afSatCount = 0;
        List<Integer> xc = new ArrayList<>();
        List<Integer> yc = new ArrayList<>();
        List<Double> flPixelVals = new ArrayList<>();
        List<Double> flGausPixelVals = new ArrayList<>();
        List<Double> afPixelVals = new ArrayList<>();
        List<Double> afGausPixelVals = new ArrayList<>();

        // Fill results containers
        for (int y = 0; y < flImp.getDimensions()[1]; y++) {
            for (int x = 0; x < flImp.getDimensions()[0]; x++) {
                if (roi.contains(x, y)) {
                    n += 1;
                    xc.add(x);
                    yc.add(y);
                    flPixelVals.add((double) flImp.getPixel(x, y)[0]);
                    flGausPixelVals.add((double) flImp2.getPixel(x, y)[0]);
                    afPixelVals.add((double) afImp.getPixel(x, y)[0]);
                    afGausPixelVals.add((double) afImp2.getPixel(x, y)[0]);

                    // Check if saturated
                    if (flImp.getPixel(x, y)[0] >= 65535) flSatCount += 1;
                    if (afImp.getPixel(x, y)[0] >= 65535) afSatCount += 1;

                }
            }
        }
        return new calEmbryoData(n, flSatCount, afSatCount, xc, yc, flPixelVals, flGausPixelVals,
                afPixelVals, afGausPixelVals);
    }


    private calEmbryoData calGetPixels3(ImagePlus imp, Roi roi) {

        // Get channels
        ImagePlus[] channels = ChannelSplitter.split(imp);
        ImagePlus flImp = channels[calChannelsHashTable.get(calFlChannel)];
        ImagePlus afImp = channels[calChannelsHashTable.get(calAfChannel)];
        ImagePlus redImp = channels[calChannelsHashTable.get(calRedChannel)];

        // Process channels
        ImagePlus flImp2 = flImp.duplicate();
        IJ.run(flImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());
        ImagePlus afImp2 = afImp.duplicate();
        IJ.run(afImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());
        ImagePlus redImp2 = redImp.duplicate();
        IJ.run(redImp2, "Gaussian Blur...", "sigma=" + calGaussianText.getText());

        // Set up results containers
        int n = 0;
        int flSatCount = 0;
        int afSatCount = 0;
        int redSatCount = 0;
        List<Integer> xc = new ArrayList<>();
        List<Integer> yc = new ArrayList<>();
        List<Double> flPixelVals = new ArrayList<>();
        List<Double> flGausPixelVals = new ArrayList<>();
        List<Double> afPixelVals = new ArrayList<>();
        List<Double> afGausPixelVals = new ArrayList<>();
        List<Double> redPixelVals = new ArrayList<>();
        List<Double> redGausPixelVals = new ArrayList<>();

        // Fill results containers
        for (int y = 0; y < flImp.getDimensions()[1]; y++) {
            for (int x = 0; x < flImp.getDimensions()[0]; x++) {
                if (roi.contains(x, y)) {
                    n += 1;
                    xc.add(x);
                    yc.add(y);
                    flPixelVals.add((double) flImp.getPixel(x, y)[0]);
                    flGausPixelVals.add((double) flImp2.getPixel(x, y)[0]);
                    afPixelVals.add((double) afImp.getPixel(x, y)[0]);
                    afGausPixelVals.add((double) afImp2.getPixel(x, y)[0]);
                    redPixelVals.add((double) redImp.getPixel(x, y)[0]);
                    redGausPixelVals.add((double) redImp2.getPixel(x, y)[0]);

                    // Check if saturated
                    if (flImp.getPixel(x, y)[0] >= 65535) flSatCount += 1;
                    if (afImp.getPixel(x, y)[0] >= 65535) afSatCount += 1;
                    if (redImp.getPixel(x, y)[0] >= 65535) redSatCount += 1;

                }
            }
        }
        return new calEmbryoData(n, flSatCount, afSatCount, redSatCount, xc, yc, flPixelVals, flGausPixelVals,
                afPixelVals, afGausPixelVals, redPixelVals, redGausPixelVals);
    }


    private void calRunRegression2(double[] flVals, double[] afVals) {

        // REGRESSION

        // Set up matrices for regression
        double[][] x = new double[flVals.length][2];
        for (int i = 0; i < flVals.length; i++) {
            x[i][0] = 1.;
            x[i][1] = afVals[i];
        }
        Matrix matrixX = new Matrix(x);
        Matrix matrixY = new Matrix(flVals, flVals.length);

        // Perform regression
        QRDecomposition qr = new QRDecomposition(matrixX);
        Matrix beta = qr.solve(matrixY);

        // Get parameters
        cal_c = beta.get(0, 0);
        cal_m1 = beta.get(1, 0);
        cal_m2 = 0;


        // CALCULATE R SQUARED

        // mean of y[] values
        double sum = 0.0;
        for (double v : flVals) sum += v;
        double mean = sum / flVals.length;

        // total variation to be accounted for
        double sst = 0.0;
        for (double v : flVals) {
            double dev = v - mean;
            sst += dev * dev;
        }

        // variation not accounted for
        Matrix residuals = matrixX.times(beta).minus(matrixY);
        double sse = residuals.norm2() * residuals.norm2();

        // R squared
        R2 = 1.0 - sse / sst;

    }


    private void calRunRegression3(double[] flVals, double[] afVals, double[] redVals) {
        // REGRESSION

        // Set up matrices for regression
        double[][] x = new double[flVals.length][3];
        for (int i = 0; i < flVals.length; i++) {
            x[i][0] = 1.;
            x[i][1] = afVals[i];
            x[i][2] = redVals[i];
        }
        Matrix matrixX = new Matrix(x);
        Matrix matrixY = new Matrix(flVals, flVals.length);

        // Perform regression
        QRDecomposition qr = new QRDecomposition(matrixX);
        Matrix beta = qr.solve(matrixY);

        // Get parameters
        cal_c = beta.get(0, 0);
        cal_m1 = beta.get(1, 0);
        cal_m2 = beta.get(2, 0);


        // CALCULATE R SQUARED

        // mean of y[] values
        double sum = 0.0;
        for (double v : flVals) sum += v;
        double mean = sum / flVals.length;

        // total variation to be accounted for
        double sst = 0.0;
        for (double v : flVals) {
            double dev = v - mean;
            sst += dev * dev;
        }

        // variation not accounted for
        Matrix residuals = matrixX.times(beta).minus(matrixY);
        double sse = residuals.norm2() * residuals.norm2();

        // R squared
        R2 = 1.0 - sse / sst;

    }


    private void calPlotRegression2(calEmbryoData[] allEmbryoData) {

        // Set up plot
        Plot plot = new Plot("Linear model", "Linear model: c + m1 * (AF channel)", "GFP channel");
        String[] colours = {"red", "green", "blue", "orange", "pink", "purple", "yellow", "brown"};

        // Loop through embryos
        for (int j = 0; j < allEmbryoData.length; j++) {

            // Calculate ypred
            double[] ypred = new double[allEmbryoData[j].flPixelVals.length];
            for (int i = 0; i < allEmbryoData[j].flPixelVals.length; i++)
                ypred[i] = cal_c + cal_m1 * allEmbryoData[j].afGausPixelVals[i];

            // Plot points
            plot.setColor(colours[j]);
            plot.addPoints(ypred, allEmbryoData[j].flGausPixelVals, Plot.DOT);

        }

        // Plot line
        plot.setColor("black");
        plot.addPoints(new double[]{0, 65536}, new double[]{0, 65536}, Plot.LINE);

        // Add equation
        plot.addLabel(0.05, 0.1, "GFP channel = c + m1 * (AF channel)\nc = " +
                String.format("%.04f", cal_c) + "\nm1 = " + String.format("%.04f", cal_m1) +
                "\n \nR² = " + String.format("%.04f", R2));

        // Show
        plot.show();

    }


    private void calPlotRegression3(calEmbryoData[] allEmbryoData) {

        // Set up plot
        Plot plot = new Plot("Linear model", "Linear model: c + m1 * (AF channel) + m2 * (RFP channel)", "GFP channel");
        String[] colours = {"red", "green", "blue", "orange", "pink", "purple", "yellow", "brown"};

        // Loop through embryos
        for (int j = 0; j < allEmbryoData.length; j++) {

            // Calculate ypred
            double[] ypred = new double[allEmbryoData[j].flPixelVals.length];
            for (int i = 0; i < allEmbryoData[j].flPixelVals.length; i++)
                ypred[i] = cal_c + cal_m1 * allEmbryoData[j].afGausPixelVals[i] + cal_m2 * allEmbryoData[j].redGausPixelVals[i];

            // Plot points
            plot.setColor(colours[j]);
            plot.addPoints(ypred, allEmbryoData[j].flGausPixelVals, Plot.DOT);

        }

        // Plot line
        plot.setColor("black");
        plot.addPoints(new double[]{0, 65536}, new double[]{0, 65536}, Plot.LINE);

        // Add equation
        plot.addLabel(0.05, 0.1, "GFP channel = c + m1 * (AF channel) + m2 * (RFP channel)\nc = " +
                String.format("%.04f", cal_c) + "\nm1 = " + String.format("%.04f", cal_m1) +
                "\nm2 = " + String.format("%.04f", cal_m2) + "\n \nR² = " + String.format("%.04f", R2));

        // Show
        plot.show();

    }


    private void calShowResids2(ImagePlus imp, String title) {
        // Get channels
        ImagePlus[] channels = ChannelSplitter.split(imp);

        // Get channels
        ImagePlus flImp = channels[calChannelsHashTable.get(calFlChannel)];
        ImagePlus afImp = channels[calChannelsHashTable.get(calAfChannel)];

        // Duplicate af image
        ImagePlus afImp3 = afImp.duplicate();

        // Calculate autofluorescence
        IJ.run(afImp3, "32-bit", "");
        IJ.run(afImp3, "Multiply...", "value=" + cal_m1 + " stack");
        IJ.run(afImp3, "Add...", "value=" + cal_c + " stack");

        // Perform subtraction -> resids
        ImageCalculator ic = new ImageCalculator();
        ImagePlus CorrectedImp = ic.run("Subtract create 32-bit stack", flImp, afImp3);

        // Close calculated AF image
        afImp3.close();

        // Set title
        CorrectedImp.setTitle("Residuals of" + title);

        // Show residuals
        CorrectedImp.show();

    }


    private void calShowResids3(ImagePlus imp, String title) {
        // Get channels
        ImagePlus[] channels = ChannelSplitter.split(imp);

        // Get images
        ImagePlus flImp = channels[calChannelsHashTable.get(calFlChannel)];
        ImagePlus afImp = channels[calChannelsHashTable.get(calAfChannel)];
        ImagePlus redImp = channels[calChannelsHashTable.get(calRedChannel)];

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
        ImagePlus CorrectedImp = ic.run("Subtract create 32-bit stack", flImp, temp);

        // Close images
        afImp3.close();
        redImp3.close();

        // Set title
        CorrectedImp.setTitle("Residuals of" + title);

        // Show residuals
        CorrectedImp.show();
    }


    private void calRunContinue() {
        calFrame.dispatchEvent(new WindowEvent(calFrame, WindowEvent.WINDOW_CLOSING));
        menuFrame.toFront();
    }


    private void calFillTable(calEmbryoData[] allEmbryoData) {
        calResultsTable = new ResultsTable();
        for (int j = 0; j < allEmbryoData.length; j++) {
            for (int i = 0; i < allEmbryoData[j].flPixelVals.length; i++) {

                // Increment counter
                calResultsTable.incrementCounter();

                // Add embryo ID
                calResultsTable.addValue("embryo_id", j);

                // Add coordinates
                calResultsTable.addValue("x_position", allEmbryoData[j].xc[i]);
                calResultsTable.addValue("y_position", allEmbryoData[j].yc[i]);

                // Add pixel values
                calResultsTable.addValue("gfp_raw", allEmbryoData[j].flPixelVals[i]);
                calResultsTable.addValue("GFP_GAUS", allEmbryoData[j].flGausPixelVals[i]);
                calResultsTable.addValue("af_raw", allEmbryoData[j].afPixelVals[i]);
                calResultsTable.addValue("af_gaus", allEmbryoData[j].afGausPixelVals[i]);
                if (!Objects.equals(calRedChannel, "<None>")) {
                    calResultsTable.addValue("rfp_raw", allEmbryoData[j].redPixelVals[i]);
                    calResultsTable.addValue("rfp_gaus", allEmbryoData[j].redGausPixelVals[i]);
                }

                // Add predicted
                double pred = cal_c + cal_m1 * allEmbryoData[j].afGausPixelVals[i];
                calResultsTable.addValue("LINEAR_MODEL", pred);

                // Add residuals
                double resid = allEmbryoData[j].flGausPixelVals[i] - pred;
                calResultsTable.addValue("RESIDUALS", resid);
            }
        }
    }


    private void calShowResTable() {
        calResultsTable.show("Pixel data");
    }

    /////////////// CORRECTION FUNCTIONS ///////////////


    private void runGetImages() {
        runImageName = (String) runImageBox.getSelectedItem();
        runFlChannel = (String) runFlChannelBox.getSelectedItem();
        runAfChannel = (String) runAfChannelBox.getSelectedItem();
        runRedChannel = (String) runRedChannelBox.getSelectedItem();

    }


    private void runRunCorrection2() {
        // Get image
        ImagePlus imp = WindowManager.getImage(runHashTable.get(runImageName));
        ImagePlus[] channels = ChannelSplitter.split(imp);

        // Get channels
        ImagePlus flImp = channels[runChannelsHashTable.get(runFlChannel)];
        ImagePlus afImp = channels[runChannelsHashTable.get(runAfChannel)];

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
        // Get image
        ImagePlus imp = WindowManager.getImage(runHashTable.get(runImageName));
        ImagePlus[] channels = ChannelSplitter.split(imp);

        // Get channels
        ImagePlus flImp = channels[runChannelsHashTable.get(runFlChannel)];
        ImagePlus afImp = channels[runChannelsHashTable.get(runAfChannel)];
        ImagePlus redImp = channels[runChannelsHashTable.get(runRedChannel)];

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

To do:
Ability to run with macros
For calibration: if it's a movie, use currently selected channel instead of first channel
Force menu window to front when cal/run windows are closed
For run: if m2 is nonzero and a red image isn't specified, throw an error
A "Use ROI" button
For run: carry over channel names from calibration
Put legend on plot to specify which embryo is which
Enforce maximum number of images (maybe 10)

Bugs:
Fails if you rerun calibration while the resids images are open (or plot window)
Will fail to show resids images if parent images are subsequently closed

 */



