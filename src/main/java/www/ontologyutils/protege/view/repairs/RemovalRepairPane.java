package www.ontologyutils.protege.view.repairs;

import java.util.Map;
import java.util.function.Predicate;

import javax.swing.*;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRemoval.BadAxiomStrategy;
import www.ontologyutils.toolbox.Ontology;

public class RemovalRepairPane extends AbstractRepairPane {
    protected BadAxiomStrategy badAxiomSource;

    @Override
    public String getName() {
        return "Removal";
    }

    @Override
    protected void addSettings() {
        var badAxiomsPanel = new JPanel();
        badAxiomsPanel.add(new JLabel("Bad axiom"));
        var badAxioms = new JComboBox<String>();
        badAxioms.addItem("Sample one inconsistent set");
        badAxioms.addItem("Sample some inconsistent set");
        badAxioms.addItem("Sample all inconsistent set");
        badAxioms.addItem("Sample one correction set");
        badAxioms.addItem("Sample some correction set");
        badAxioms.addItem("Sample all correction set");
        badAxioms.addItem("Smallest correction set");
        badAxioms.addItem("Random");
        badAxioms.setSelectedIndex(0);
        var goals = Map.<String, BadAxiomStrategy>of(
                "Sample one inconsistent set", BadAxiomStrategy.IN_ONE_MUS,
                "Sample some inconsistent set", BadAxiomStrategy.IN_SOME_MUS,
                "Sample all inconsistent set", BadAxiomStrategy.IN_MOST_MUS,
                "Sample one correction set", BadAxiomStrategy.NOT_IN_ONE_MCS,
                "Sample some correction set", BadAxiomStrategy.NOT_IN_SOME_MCS,
                "Sample all correction set", BadAxiomStrategy.IN_LEAST_MCS,
                "Smallest correction set", BadAxiomStrategy.NOT_IN_LARGEST_MCS,
                "Random", BadAxiomStrategy.RANDOM);
        badAxiomSource = goals.get(badAxioms.getItemAt(badAxioms.getSelectedIndex()));
        badAxioms.addActionListener(e -> {
            badAxiomSource = goals.get(badAxioms.getItemAt(badAxioms.getSelectedIndex()));
        });
        badAxiomsPanel.add(badAxioms);
        add(badAxiomsPanel);
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairRemoval(isRepaired, badAxiomSource);
    }
}
