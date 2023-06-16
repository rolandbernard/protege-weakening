package www.ontologyutils.protege.list;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.*;

import org.protege.editor.core.ui.list.MListButton;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.ui.list.OWLAxiomList;
import org.protege.editor.owl.ui.renderer.*;
import org.semanticweb.owlapi.model.*;

import www.ontologyutils.protege.button.*;
import www.ontologyutils.toolbox.MinimalSubsets;
import www.ontologyutils.toolbox.Ontology;
import www.ontologyutils.toolbox.Utils;

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
        buttons.add(new StrengthenAxiomButton((AxiomListItem) value, editorKit));
        buttons.add(new WeakenAxiomButton((AxiomListItem) value, editorKit));
        return buttons;
    }

    public Map<OWLAxiom, Long> getAxiomCounts(OWLOntology owlOntology) {
        var reasonerFactory = editorKit.getOWLModelManager().getOWLReasonerManager()
                .getCurrentReasonerFactory().getReasonerFactory();
        try (var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory)) {
            var refutableAxioms = Utils.toSet(ontology.refutableAxioms());
            return MinimalSubsets.randomizedMinimalSubsets(refutableAxioms, 1, axioms -> {
                try (var copy = ontology.cloneWithRefutable(axioms)) {
                    return !copy.isConsistent();
                }
            }).flatMap(set -> set.stream())
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        }
    }

    @SuppressWarnings("unchecked")
    public void updateAxiomList() {
        var owlOntology = editorKit.getOWLModelManager().getActiveOntology();
        var occurrences = getAxiomCounts(owlOntology);
        var axiomItems = owlOntology.getLogicalAxioms().stream()
                .sorted((a, b) -> Long.compare(occurrences.getOrDefault(b, 0L), occurrences.getOrDefault(a, 0L)))
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
