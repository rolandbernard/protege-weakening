package www.ontologyutils.protege;

import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.protege.editor.owl.ui.action.ProtegeOWLAction;

public class TestMenu extends ProtegeOWLAction {
    public void initialise() throws Exception {
    }

    public void dispose() throws Exception {
    }

    public void actionPerformed(final ActionEvent event) {
        StringBuilder message = new StringBuilder();
        message.append("Some example text.");
        JOptionPane.showMessageDialog(getOWLWorkspace(), message.toString());
    }
}
