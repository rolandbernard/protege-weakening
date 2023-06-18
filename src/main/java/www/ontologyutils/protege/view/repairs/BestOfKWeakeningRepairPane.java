package www.ontologyutils.protege.view.repairs;

import java.util.function.Predicate;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import www.ontologyutils.repair.*;
import www.ontologyutils.toolbox.Ontology;

public class BestOfKWeakeningRepairPane extends WeakeningRepairPane {
    protected int numberOfRounds;

    @Override
    public String getName() {
        return "\"best\" of k Weakening";
    }

    @Override
    protected void addSettings() {
        super.addSettings();

        var roundsPanel = new JPanel();
        roundsPanel.add(new JLabel("Number of Rounds"));
        var rounds = new JTextField("100");
        numberOfRounds = 100;
        rounds.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent arg0) {
                try {
                    numberOfRounds = Integer.parseUnsignedInt(rounds.getText());
                } catch (NumberFormatException ex) {
                }
            }

            @Override
            public void insertUpdate(DocumentEvent arg0) {
                changedUpdate(arg0);
            }

            @Override
            public void removeUpdate(DocumentEvent arg0) {
                changedUpdate(arg0);
            }
        });
        rounds.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                rounds.setText(Integer.toString(numberOfRounds));
            });
        });
        roundsPanel.add(rounds);
        add(roundsPanel);
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairBestOfKWeakening(isRepaired, refOntologySource, badAxiomSource, weakeningFlags,
                enhanceRef, o -> (double) o.inferredTaxonomyAxioms().count(), numberOfRounds);
    }
}
