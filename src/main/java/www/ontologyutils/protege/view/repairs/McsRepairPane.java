package www.ontologyutils.protege.view.repairs;

import javax.swing.*;

import java.util.Map;
import java.util.function.Predicate;

import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairRandomMcs.McsComputationStrategy;
import www.ontologyutils.toolbox.Ontology;

public class McsRepairPane extends AbstractRepairPane {
    private McsComputationStrategy mcsStrategy;

    @Override
    public String getName() {
        return "Maximal Consistent Subset";
    }

    @Override
    protected void addSettings() {
        var mcsPanel = new JPanel();
        mcsPanel.add(new JLabel("Compute"));
        var mcs = new JComboBox<String>();
        mcs.addItem("One maximal consistent set");
        mcs.addItem("Some maximal consistent set");
        mcs.addItem("All maximal consistent set");
        mcs.setSelectedIndex(0);
        var goals = Map.<String, McsComputationStrategy>of(
                "One maximal consistent set", McsComputationStrategy.ONE_MCS,
                "Some maximal consistent set", McsComputationStrategy.SOME_MCS,
                "All maximal consistent set", McsComputationStrategy.ALL_MCS);
        mcsStrategy = goals.get(mcs.getItemAt(mcs.getSelectedIndex()));
        mcs.addActionListener(e -> {
            mcsStrategy = goals.get(mcs.getItemAt(mcs.getSelectedIndex()));
        });
        mcsPanel.add(mcs);
        add(mcsPanel);
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairRandomMcs(isRepaired, mcsStrategy);
    }
}
