package www.ontologyutils.protege.list;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import org.protege.editor.core.ui.list.MListButton;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.ui.list.OWLAxiomList;
import org.protege.editor.owl.ui.renderer.*;
import org.semanticweb.owlapi.model.*;

import www.ontologyutils.protege.button.WeakenAxiomButton;

public class AxiomWeakeningList extends OWLAxiomList implements LinkedObjectComponent {
    private OWLEditorKit editorKit;
    private LinkedObjectComponentMediator mediator;

    public AxiomWeakeningList(OWLEditorKit editorKit) {
        super(editorKit);
        this.editorKit = editorKit;
        mediator = new LinkedObjectComponentMediator(editorKit, this);
        setCellRenderer(new AxiomCellRenderer(editorKit));
        getMouseListeners();
    }

    @Override
    protected List<MListButton> getButtons(Object value) {
        var buttons = new ArrayList<MListButton>();
        buttons.addAll(super.getButtons(value));
        buttons.add(new WeakenAxiomButton((AxiomListItem) value, editorKit));
        return buttons;
    }

    @SuppressWarnings("unchecked")
    public void updateAxiomList() {
        var owlOntology = editorKit.getOWLModelManager().getActiveOntology();
        var axiomItems = owlOntology.getLogicalAxioms().stream()
                .map(axiom -> new AxiomListItem(axiom, owlOntology))
                .toArray(n -> new AxiomListItem[n]);
        setListData(axiomItems);
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
