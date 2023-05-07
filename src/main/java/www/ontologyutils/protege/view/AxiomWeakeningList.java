package www.ontologyutils.protege.view;

import java.awt.*;
import javax.swing.*;

import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.ui.list.OWLAxiomList;
import org.protege.editor.owl.ui.renderer.*;
import org.semanticweb.owlapi.model.*;

public class AxiomWeakeningList extends OWLAxiomList implements LinkedObjectComponent {
    private OWLOntology owlOntology;
    private OWLCellRenderer renderer;
    private LinkedObjectComponentMediator mediator;

    public AxiomWeakeningList(OWLEditorKit editorKit) {
        super(editorKit);
        mediator = new LinkedObjectComponentMediator(editorKit, this);
        renderer = new OWLCellRenderer(editorKit);
        renderer.setHighlightKeywords(true);
        renderer.setWrap(true);
        setCellRenderer(renderer);
    }

    public void setOwlOntology(OWLOntology owlOntology) {
        this.owlOntology = owlOntology;
        updateAxiomList();
    }

    @SuppressWarnings("unchecked")
    public void updateAxiomList() {
        var axioms = owlOntology.getLogicalAxioms().toArray(new OWLAxiom[0]);
        setListData(axioms);
    }

    @Override
    public Point getMouseCellLocation() {
        Point mousePosition = getMousePosition();
        if (mousePosition == null) {
            return null;
        }
        int index = locationToIndex(mousePosition);
        Rectangle cellRect = getCellBounds(index, index);
        return new Point(mousePosition.x - cellRect.x, mousePosition.y - cellRect.y);
    }

    @Override
    public Rectangle getMouseCellRect() {
        Point mousePosition = getMousePosition();
        if (mousePosition == null) {
            return null;
        }
        int index = locationToIndex(mousePosition);
        return getCellBounds(index, index);
    }

    @Override
    public void setLinkedObject(OWLObject object) {
        mediator.setLinkedObject(object);
    }

    @Override
    public OWLObject getLinkedObject() {
        return mediator.getLinkedObject();
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    public void dispose() {
    }
}
