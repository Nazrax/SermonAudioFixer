package church.covenantrpcus.synchelper;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NONE;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class Main {
  protected static final String LAST_VIDEO_PATH_KEY = "last_video_path";
  protected static final String LAST_AUDIO_PATH_KEY = "last_audio_path";
  private static final String CRF = "26";
  static MutableFile selectedVideoFile = new MutableFile(), selectedAudioFile = new MutableFile();
  static JFrame frame;
  static Preferences prefs;
  static JLabel videoPathLabel, audioPathLabel;
  static JButton extractAudioButton, chooseVideoFileButton, chooseAudioFileButton, generatePreviewsButton;
  private static JTextField offsetField, startTimeField, endTimeField;
private static JButton generateOutputButton;
  
  static void disableButtons() {
    extractAudioButton.setEnabled(false);
    chooseVideoFileButton.setEnabled(false);
    chooseAudioFileButton.setEnabled(false);
    generatePreviewsButton.setEnabled(false);
    generateOutputButton.setEnabled(false);
  }
  
  static void conditionallyEnableButtons() {
    chooseVideoFileButton.setEnabled(true);
    chooseAudioFileButton.setEnabled(true);
    
    if (selectedVideoFile.file != null) {
      extractAudioButton.setEnabled(true);
    } else {
      extractAudioButton.setEnabled(false);
    }
    
    if (!offsetField.getText().isEmpty() && !offsetField.equals("0.00") &&
        !startTimeField.getText().isEmpty() && !startTimeField.getText().equals("00:00:00.00") && 
        !endTimeField.getText().isEmpty() && !endTimeField.getText().equals("00:00:00.00") &&
        selectedAudioFile.file != null && selectedVideoFile.file != null) {
        generatePreviewsButton.setEnabled(true);
        generateOutputButton.setEnabled(true);
    } else {
      generatePreviewsButton.setEnabled(false);
      generateOutputButton.setEnabled(false);
    }
  }
  
  static boolean runFFmpeg(String[] arguments, String suffix, final JLabel label, String labelPrefix) {
    final String outputPath = selectedVideoFile.file.getAbsolutePath().replaceAll("\\.[^\\.]+$", "") + "-" + suffix;
    final String logPath = selectedVideoFile.file.getAbsolutePath().replaceAll("\\.[^\\.]+$", "") + "-" + suffix + ".log";
    final List<String> cmdList = new ArrayList<String>();
    cmdList.add("ffmpeg");
    cmdList.add("-n");
    Collections.addAll(cmdList, arguments);
    cmdList.add(outputPath);
    disableButtons();
    boolean success = false;
    
    if (new File(outputPath).exists()) {
      JOptionPane.showMessageDialog(frame, "Output file " + outputPath + " already exists", "File already exists", JOptionPane.ERROR_MESSAGE);
      conditionallyEnableButtons();
      return false;
    } else if (new File(logPath).exists()) {
      JOptionPane.showMessageDialog(frame, "Output file " + logPath + " already exists", "File already exists", JOptionPane.ERROR_MESSAGE);
      conditionallyEnableButtons();
      return false;
    }
    
    StringBuffer cmdString = new StringBuffer();
    for(String s : cmdList) {
      cmdString.append(" " + s);
    }
    System.out.println("Executing command: " + cmdString);
    if (labelPrefix != null) {
      labelPrefix = labelPrefix + ": ";
    } else {
      labelPrefix = "";
    }
    
    try {
      Process process = Runtime.getRuntime().exec(cmdList.toArray(new String[cmdList.size()]));
      InputStream stream = process.getErrorStream();
      FileOutputStream fos = new FileOutputStream(logPath);
      fos.write(("Executing command: " + cmdString.toString()).getBytes());
      StringBuffer buf = new StringBuffer();
      int i;
      Pattern p = Pattern.compile("^.*time=(\\d\\d:\\d\\d:\\d\\d.\\d\\d) .*speed=\\s*(.*x)\\s*$");
      while ((i = stream.read()) != -1) {
        char c = (char)i;
        System.out.write(c);
        fos.write(c);
        if (c == '\r') {
          String line = buf.toString();
          System.out.println("Line: " + line);
          buf = new StringBuffer();

          if (label != null) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
              label.setText(labelPrefix + "Progress: " + m.group(1) + " (" + m.group(2) + ")");
            }
          }
        } else {
          buf.append(c);
        }
      }
      fos.close();
      int rv = process.waitFor();
      if (rv == 0) {
        if (label != null) {
          label.setText("All done");
        }
        success = true;
      } else {
        if (label != null) {
          label.setText("Something went wrong");
        }
        JOptionPane.showMessageDialog(frame, "Check the log at\n" +  logPath + "\nfor details", "Conversion failed", JOptionPane.ERROR_MESSAGE);
      }
    } catch (IOException e1) {
      e1.printStackTrace();
    } catch (InterruptedException e1) {
      e1.printStackTrace();
    }
    conditionallyEnableButtons();
    return success;
  }

  public static String[] createEncodeCommand(String seek, String[] backArgs) {
    List<String> baseCmd = Arrays.asList(new String[] {
        "-i", selectedVideoFile.file.getAbsolutePath(),
        "-itsoffset", offsetField.getText(), "-i", selectedAudioFile.file.getAbsolutePath(),
        "-map", "0:0", "-map", "1:0",
        "-c:a", "aac", "-strict", "-2", "-b:a", "64k",
        "-c:v", "libx264", "-crf", CRF, "-vf", "yadif", "-profile:v", "high", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", "-bf", "2", "-flags", "+cgop",
        "-ss", seek, "-threads", "0"
//        "-preset", "ultrafast", "-t", "20", "-ss", startTimeField.getText()
    });
    
    List<String> backArgList = Arrays.asList(backArgs);
    List<String> cmd = new ArrayList<String>();
    cmd.addAll(baseCmd);
    cmd.addAll(backArgList);
    return cmd.toArray(new String[cmd.size()]);
  }
  
  public static void main(String[] args) {
    prefs = Preferences.userRoot().node("/net/shadowspire/RenderHelper");
    //boolean inWindows = System.getProperty("os.name").startsWith("Windows");
    
    // Frame
    frame = new JFrame("Sermon Audio Replacer");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Step 1
    JPanel step1Panel = new JPanel();
    step1Panel.setBorder(BorderFactory.createTitledBorder("Step 1: Extract audio"));
    step1Panel.setLayout(new GridBagLayout());
    chooseVideoFileButton = new JButton("Choose video file");
    step1Panel.add(chooseVideoFileButton, gbc(0, 0, 1, 1, 0, 0, NONE));
    videoPathLabel = new JLabel("Choose a video file                                              ");
    step1Panel.add(videoPathLabel, gbc(1, 0, 1, 1, 1, 0, HORIZONTAL));
    extractAudioButton = new JButton("Extract audio");
    extractAudioButton.setEnabled(false);
    step1Panel.add(extractAudioButton, gbc(0, 1, 1, 1, 0, 0, NONE));
    final JLabel step1ProgressLabel = new JLabel ("No progress to report");
    step1Panel.add(step1ProgressLabel, gbc(1, 1, 1, 1, 1, 0, HORIZONTAL));
    
    chooseVideoFileButton.addActionListener(new ChooseFileActionListener(selectedVideoFile, LAST_VIDEO_PATH_KEY, videoPathLabel, "m2ts"));
    
    extractAudioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String[] cmdArray = new String[] {
            "-i", selectedVideoFile.file.getAbsolutePath(),
            "-b:a", "48k"
        };
        new Thread() { public void run() { runFFmpeg(cmdArray, "low-quality-audio.mp3", step1ProgressLabel, null); } }.start();
      }});
    
    // Step 2
    JPanel step2Panel = new JPanel();
    step2Panel.setLayout(new GridBagLayout());
    step2Panel.setBorder(BorderFactory.createTitledBorder("Step 2: Audio information"));
    
    chooseAudioFileButton = new JButton("Choose audio file");
    step2Panel.add(chooseAudioFileButton, gbc(0, 0, 1, 1, 0, 0, NONE));
    audioPathLabel = new JLabel("Choose an MP3 file                                              ");
    step2Panel.add(audioPathLabel, gbc(1, 0, 1, 1, 1, 0, HORIZONTAL));

    chooseAudioFileButton.addActionListener(new ChooseFileActionListener(selectedAudioFile, LAST_AUDIO_PATH_KEY, audioPathLabel, "mp3"));

    step2Panel.add(new JLabel("Offset:"), gbc(0, 1, 1, 1, 0, 0, NONE));
    step2Panel.add(new JLabel("Start time:"), gbc(0, 2, 1, 1, 0, 0, NONE));
    step2Panel.add(new JLabel("End time:"), gbc(0, 3, 1, 1, 0, 0, NONE));
    
    offsetField = new JTextField("0.00");
    startTimeField = new JTextField("00:00:00.00");
    endTimeField = new JTextField("00:00:00.00");
    
    step2Panel.add(offsetField, gbc(1, 1, 1, 1, 1, 0, HORIZONTAL));
    step2Panel.add(startTimeField, gbc(1, 2, 1, 1, 1, 0, HORIZONTAL));
    step2Panel.add(endTimeField, gbc(1, 3, 1, 1, 1, 0, HORIZONTAL));
    
    DocumentListener dl = new DocumentListener() {
      @Override
      public void changedUpdate(DocumentEvent arg0) {
        conditionallyEnableButtons();
      }

      @Override
      public void insertUpdate(DocumentEvent arg0) {
        conditionallyEnableButtons();
      }

      @Override
      public void removeUpdate(DocumentEvent arg0) {
        conditionallyEnableButtons();
      }
    };
    
    offsetField.getDocument().addDocumentListener(dl);
    startTimeField.getDocument().addDocumentListener(dl);
    endTimeField.getDocument().addDocumentListener(dl);
    
    offsetField.addFocusListener(new FocusAction(offsetField));
    startTimeField.addFocusListener(new FocusAction(startTimeField));
    endTimeField.addFocusListener(new FocusAction(endTimeField));
    
    // Step 3
    JPanel step3Panel = new JPanel();
    step3Panel.setLayout(new GridBagLayout());
    step3Panel.setBorder(BorderFactory.createTitledBorder("Step 3: Preview"));

    generatePreviewsButton = new JButton("Generate preview");
    generatePreviewsButton.setEnabled(false);
    step3Panel.add(generatePreviewsButton, gbc(0, 0, 1, 1, 0, 0, NONE));

    final JLabel step3ProgressLabel = new JLabel ("No progress to report");
    step3Panel.add(step3ProgressLabel, gbc(1, 0, 1, 1, 1, 0, HORIZONTAL));

    generatePreviewsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        new Thread() {
          //Double endPreviewStartTime = timeToSeconds(endTimeField.getText()) - 20;
          String[] startCmdArray = createEncodeCommand(startTimeField.getText(), new String[] { "-preset", "ultrafast", "-t", "20", "-vf", "scale=720:540" });
          //String[] endCmdArray = createEncodeCommand(endPreviewStartTime.toString(), new String[] { "-preset", "ultrafast", "-t", "20" });

          public void run() {
            step3ProgressLabel.setText("Generating preview");
            boolean rv = runFFmpeg(startCmdArray, "preview.mp4", step3ProgressLabel, null);
            if (!rv) {
              step3ProgressLabel.setText("Start preview creation failed");
            } else {
              step3ProgressLabel.setText("All done");
            }
          }
        }.start();
      }});

    // Step 4
    JPanel step4Panel = new JPanel();
    step4Panel.setLayout(new GridBagLayout());
    step4Panel.setBorder(BorderFactory.createTitledBorder("Step 4: Final output"));

    generateOutputButton = new JButton("Generate output");
    generateOutputButton.setEnabled(false);
    step4Panel.add(generateOutputButton, gbc(0, 0, 1, 1, 0, 0, NONE));

    final JLabel step4ProgressLabel = new JLabel ("No progress to report");
    step4Panel.add(step4ProgressLabel, gbc(1, 0, 1, 1, 1, 0, HORIZONTAL));

    generateOutputButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
          final String[] cmdArray = createEncodeCommand(
        		  startTimeField.getText(), 
        		  new String[] { "-preset", "slow", "-to", endTimeField.getText() }); // TODO Change preset to slow
          new Thread() { public void run() { runFFmpeg(cmdArray, "final.mp4", step4ProgressLabel, null); } }.start();
      }});

    
    // Outer panel
    JPanel outerPanel = new JPanel();
    outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.PAGE_AXIS));
    outerPanel.add(step1Panel);
    outerPanel.add(step2Panel);
    outerPanel.add(step3Panel);
    outerPanel.add(step4Panel);

    frame.setContentPane(outerPanel);
    frame.pack();
    frame.setVisible(true);
  }

  private static GridBagConstraints gbc(int x, int y, int width, int height, int weightx, int weighty, int fill) {
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = x;
    c.gridy = y;
    c.gridwidth = width;
    c.gridheight = height;
    c.fill = fill;
    c.weightx = weightx;
    c.weighty = weighty;
    return c;
  }

  public static double timeToSeconds(String text) {
    String[] split = text.split(":");
    double hour = 0, minute = 0, second;

    if (split.length == 1) {
      second = Double.parseDouble(text);
    } else if (split.length == 2) {
      minute = Double.parseDouble(split[0]);
      second = Double.parseDouble(split[1]);
    } else {
      hour = Double.parseDouble(split[0]);
      minute = Double.parseDouble(split[1]);
      second = Double.parseDouble(split[2]);
    }
    return hour * 3600 + minute * 60 + second;
  }

}
