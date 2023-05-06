package www.ontologyutils.protege.view;

import java.awt.BorderLayout;

import javax.swing.JScrollPane;

import org.protege.editor.owl.model.event.OWLModelManagerChangeEvent;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.protege.editor.owl.ui.list.OWLAxiomList;
import org.protege.editor.owl.ui.renderer.OWLCellRenderer;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;
import org.semanticweb.owlapi.model.OWLAxiom;

public class AxiomListWeakeningView extends AbstractOWLViewComponent implements OWLModelManagerListener {
    private OWLAxiomList axiomList;
    private OWLCellRenderer renderer;

    @Override
    protected void initialiseOWLView() throws Exception {
        setLayout(new BorderLayout());
        axiomList = new OWLAxiomList(getOWLEditorKit());
        renderer = new OWLCellRenderer(getOWLEditorKit());
        renderer.setHighlightKeywords(true);
        renderer.setWrap(true);
        axiomList.setCellRenderer(renderer);
        var scrollPane = new JScrollPane(axiomList);
        add(scrollPane, BorderLayout.CENTER);
        getOWLModelManager().addListener(this);
        handleChange(null);
    }

    @Override
    protected void disposeOWLView() {
        getOWLModelManager().removeListener(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handleChange(OWLModelManagerChangeEvent e) {
        var axioms = getOWLModelManager().getActiveOntology().getAxioms().toArray(new OWLAxiom[0]);
        axiomList.setListData(axioms);
        renderer.setOntology(getOWLModelManager().getActiveOntology());
    }
}
