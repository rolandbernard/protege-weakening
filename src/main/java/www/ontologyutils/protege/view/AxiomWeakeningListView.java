package www.ontologyutils.protege.view;

import java.util.List;

import javax.swing.*;
import java.awt.*;

import org.protege.editor.owl.model.event.*;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import org.semanticweb.owlapi.model.*;

public class AxiomWeakeningListView extends AbstractOWLViewComponent implements OWLModelManagerListener, OWLOntologyChangeListener {
    private AxiomWeakeningList axiomList;
    private OWLOntologyManager owlOntologyManager;

    @Override
    protected void initialiseOWLView() throws Exception {
        setLayout(new BorderLayout());
        axiomList = new AxiomWeakeningList(getOWLEditorKit());
        var scrollPane = new JScrollPane(axiomList);
        add(scrollPane, BorderLayout.CENTER);
        getOWLModelManager().addListener(this);
        updateState();
    }

    @Override
    protected void disposeOWLView() {
        getOWLModelManager().removeListener(this);
    }

    private void updateState() {
        var owlOntology = getOWLModelManager().getActiveOntology();
        if (owlOntologyManager != owlOntology.getOWLOntologyManager()) {
            if (owlOntologyManager != null) {
                owlOntologyManager.removeOntologyChangeListener(this);
            }
            owlOntologyManager = owlOntology.getOWLOntologyManager();
            owlOntologyManager.addOntologyChangeListener(this);
        }
        axiomList.setOwlOntology(owlOntology);
    }

    @Override
    public void handleChange(OWLModelManagerChangeEvent e) {
        updateState();
    }

    @Override
    public void ontologiesChanged(List<? extends OWLOntologyChange> changes) {
        updateState();
    }
}
