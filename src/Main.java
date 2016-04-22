import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;

public class Main {
  protected static final String LAST_PATH_KEY = "last path";
  static File selectedFile;
  static JFrame frame;
  
  static void runFFmpeg(String[] arguments, String suffix, final JLabel label) {
    final String outputPath = selectedFile.getAbsolutePath().replaceAll("\\.[^\\.]+$", "") + "-" + suffix;
    final String logPath = selectedFile.getAbsolutePath().replaceAll("\\.[^\\.]+$", "") + "-" + suffix + ".log";
    final List<String> cmdList = new ArrayList<String>();
    cmdList.add("/home/aabraham/software/bin/ffmpeg");
    cmdList.add("-yaoeu");
    Collections.addAll(cmdList, arguments);
    cmdList.add(outputPath);

    new Thread() {
      public void run() {
        try {
          Process process = Runtime.getRuntime().exec(cmdList.toArray(new String[cmdList.size()]));
          InputStream stream = process.getErrorStream();
          FileOutputStream fos = new FileOutputStream(logPath);
          StringBuffer buf = new StringBuffer();
          int i;
          Pattern p = Pattern.compile("^.*time=(\\d\\d:\\d\\d:\\d\\d.\\d\\d) .*speed=(.*x)\\s*$");
          while ((i = stream.read()) != -1) {
            char c = (char)i;
            System.out.write(c);
            fos.write(c);
            if (c == '\r') {
              String line = buf.toString();
              System.out.println("Line: " + line);
              buf = new StringBuffer();

              Matcher m = p.matcher(line);
              if (m.matches()) {
                label.setText("Progress: " + m.group(1) + " (" + m.group(2) + ")");
              }
            } else {
              buf.append(c);
            }
          }
          fos.close();
          int rv = process.waitFor();
          if (rv == 0) {
            label.setText("All done");
          } else {
            label.setText("Something went wrong");
            JOptionPane.showMessageDialog(frame, "Check the log at\n" +  logPath + "\nfor details", "Conversion failed", JOptionPane.ERROR_MESSAGE);
          }
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        } catch (InterruptedException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      }
    }.start();
  }
  
  public static void main(String[] args) {
    final Preferences prefs = Preferences.userRoot().node("/net/shadowspire/RenderHelper");
    boolean inWindows = System.getProperty("os.name").startsWith("Windows");
    
    // Frame
    frame = new JFrame("Sync Helper");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // Step 1
    JPanel step1Panel = new JPanel();
    step1Panel.setBorder(BorderFactory.createTitledBorder("Step 1: Extract audio"));
    step1Panel.setLayout(new BorderLayout());
    JButton chooseInputFileButton = new JButton("Choose video file");
    step1Panel.add(chooseInputFileButton, BorderLayout.WEST);
    final JLabel filePathLabel = new JLabel("Choose a video file");
    step1Panel.add(filePathLabel, BorderLayout.CENTER);
    final JButton extractAudioButton = new JButton("Extract audio");
    extractAudioButton.setEnabled(false);
    step1Panel.add(extractAudioButton, BorderLayout.SOUTH);
    final JLabel step1ProgressLabel = new JLabel ("No progress to report");
    step1Panel.add(step1ProgressLabel, BorderLayout.NORTH);
    
    chooseInputFileButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(false);
        if (selectedFile != null) {
          chooser.setCurrentDirectory(selectedFile.getParentFile());
          chooser.ensureFileIsVisible(selectedFile);
        } else {
          String path = prefs.get(LAST_PATH_KEY, null);
          if (path != null) {
            File lastDir = new File(path);
            if (lastDir.exists() && lastDir.isDirectory()) {
              chooser.setCurrentDirectory(lastDir);
            }
          }
        }
        chooser.setFileFilter(new FileFilter() {
          @Override
          public boolean accept(File file) {
            return file.isDirectory() || file.getName().toLowerCase().endsWith(".m2ts");
          }

          @Override
          public String getDescription() {
            return "M2TS Files";
          }});
        int rv = chooser.showOpenDialog(frame);
        if (rv == JFileChooser.APPROVE_OPTION) {
          selectedFile = chooser.getSelectedFile();
          filePathLabel.setText(selectedFile.getPath());
          prefs.put(LAST_PATH_KEY, selectedFile.getParent());
          try {
            prefs.flush();
          } catch (BackingStoreException e1) {
            e1.printStackTrace();
          }
          extractAudioButton.setEnabled(true);
          frame.pack();
        }
      }
    });
    
    extractAudioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String[] cmdArray = new String[] {
            "-i", selectedFile.getAbsolutePath(),
            "-b:a", "48k"
        };
        runFFmpeg(cmdArray, "low-quality-audio.mp3", step1ProgressLabel);
      }});
    
    // Step 2
    JPanel step2Panel = new JPanel();
    step2Panel.setBorder(BorderFactory.createTitledBorder("Step 2: Stuff"));

    // Outer panel
    JPanel outerPanel = new JPanel();
    outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.PAGE_AXIS));
    outerPanel.add(step1Panel);
    outerPanel.add(step2Panel);

    frame.setContentPane(outerPanel);
    frame.pack();
    frame.setVisible(true);
  }
}
