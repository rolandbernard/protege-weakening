package www.ontologyutils.protege.button;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.protege.editor.core.ui.list.MListButton;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.model.OWLAxiom;

import www.ontologyutils.protege.list.AxiomListItem;
import www.ontologyutils.refinement.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs;
import www.ontologyutils.toolbox.*;

public abstract class AbstractAxiomRefinementButton extends MListButton {
    private AxiomListItem item;
    private OWLEditorKit editorKit;

    public AbstractAxiomRefinementButton(String name, AxiomListItem item, OWLEditorKit editorKit) {
        super(name, Color.GREEN.darker());
        this.item = item;
        this.editorKit = editorKit;
        this.setActionListener(this::performAction);
    }

    public abstract AxiomRefinement getRefinement(Ontology refOntology, Ontology fullOntology);

    private OWLAxiom selectFromOptions(Stream<OWLAxiom> axioms) {
        return (OWLAxiom) JOptionPane.showInputDialog(
                editorKit.getOWLWorkspace(),
                "Which axiom to choose?",
                "Choose an axiom",
                JOptionPane.QUESTION_MESSAGE,
                null,
                axioms.toArray(),
                null);
    }

    private void performAction(ActionEvent e) {
        var reasonerFactory = editorKit.getOWLModelManager().getOWLReasonerManager()
                .getCurrentReasonerFactory().getReasonerFactory();
        var owlOntology = item.getOntology();
        try (var ontology = Ontology.withAxiomsFrom(owlOntology, reasonerFactory)) {
            try (var refOntology = OntologyRepairRandomMcs.forConsistency().modified(ontology)) {
                var axiom = item.getAxiom();
                // Remove the axiom before weakening, because otherwise the weakening assumes
                // the axiom holds.
                refOntology.removeAxioms(axiom);
                try (var refinement = getRefinement(refOntology, ontology)) {
                    var newAxiom = selectFromOptions(refinement.refineAxioms(axiom));
                    if (newAxiom == null) {
                        return;
                    }
                    ontology.replaceAxiom(axiom, newAxiom);
                }
            }
            ontology.applyChangesTo(owlOntology);
        }
    }

    public void drawArrow(Graphics2D gOuter, double rotate) {
        Graphics2D g = (Graphics2D) gOuter.create();
        int size = getBounds().height;
        int thickness = (Math.round(size / 8.0f) / 2) * 2;
        int x = getBounds().x;
        int y = getBounds().y;
        g.rotate(rotate, x + size / 2, y + size / 2);
        g.fillRect(x + size / 2 - thickness / 2, y + size / 4, thickness, size / 2);
        g.rotate(Math.PI / 4, x + size / 2, y + size / 2);
        g.fillRect(x + size / 4 + thickness / 2, y + size / 4 + thickness / 2, thickness, size / 3);
        g.fillRect(x + size / 4 + thickness / 2, y + size / 4 + thickness / 2, size / 3, thickness);
    }
}
