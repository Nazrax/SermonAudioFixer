package church.covenantrpcus.synchelper;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JTextField;

public class FocusAction implements FocusListener {
  private JTextField field;

  public FocusAction(JTextField field) {
    this.field = field;
  }
  
  @Override
  public void focusGained(FocusEvent arg0) {
    field.select(0, field.getText().length());
  }

  @Override
  public void focusLost(FocusEvent arg0) {
  }
}
