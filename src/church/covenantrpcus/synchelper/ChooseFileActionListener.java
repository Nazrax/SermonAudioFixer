package church.covenantrpcus.synchelper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.BackingStoreException;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.filechooser.FileFilter;

public class ChooseFileActionListener implements ActionListener {
  private MutableFile file;
  private String key;
  private JLabel label;
  private String extension;

  public ChooseFileActionListener(MutableFile file, String key, JLabel label, String extension) {
    this.file = file;
    this.key = key;
    this.label = label;
    this.extension = extension;
  }
  
  @Override
  public void actionPerformed(ActionEvent e) {
    JFileChooser chooser = new JFileChooser();
    chooser.setMultiSelectionEnabled(false);
    if (file.file != null) {
      chooser.setCurrentDirectory(file.file.getParentFile());
      chooser.ensureFileIsVisible(file.file);
    } else {
      String path = Main.prefs.get(key, null);
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
        return file.isDirectory() || file.getName().toLowerCase().endsWith("." + extension);
      }

      @Override
      public String getDescription() {
        return extension +  " files";
      }});
    int rv = chooser.showOpenDialog(Main.frame);
    if (rv == JFileChooser.APPROVE_OPTION) {
      file.file = chooser.getSelectedFile();
      label.setText(file.file.getPath());
      Main.prefs.put(key, file.file.getParent());
      try {
        Main.prefs.flush();
      } catch (BackingStoreException e1) {
        e1.printStackTrace();
      }
      Main.conditionallyEnableButtons();
      Main.frame.pack();
    }
  }
}
