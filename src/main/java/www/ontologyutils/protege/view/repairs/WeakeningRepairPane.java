package www.ontologyutils.protege.view.repairs;

import java.util.Map;
import java.util.function.Predicate;

import javax.swing.*;

import www.ontologyutils.refinement.AxiomWeakener;
import www.ontologyutils.repair.*;
import www.ontologyutils.repair.OntologyRepairWeakening.RefOntologyStrategy;
import www.ontologyutils.toolbox.Ontology;

public class WeakeningRepairPane extends RemovalRepairPane {
    protected RefOntologyStrategy refOntologySource;
    protected int weakeningFlags = 0;
    protected boolean enhanceRef = false;

    @Override
    public String getName() {
        return "Weakening";
    }

    private void setFlag(int flag, boolean value) {
        if (value) {
            weakeningFlags |= flag;
        } else {
            weakeningFlags &= ~flag;
        }
    }

    @Override
    protected void addSettings() {
        super.addSettings();

        var refOntologyPanel = new JPanel();
        refOntologyPanel.add(new JLabel("Reference ontology"));
        var refOntology = new JComboBox<String>();
        refOntology.addItem("Random maximal consistent set");
        refOntology.addItem("Intersection of some maximal consistent set");
        refOntology.addItem("Intersection of all maximal consistent set");
        refOntology.addItem("Largest maximal consistent set");
        refOntology.setSelectedIndex(0);
        var goals = Map.<String, RefOntologyStrategy>of(
                "Random maximal consistent set", RefOntologyStrategy.ONE_MCS,
                "Intersection of some maximal consistent set", RefOntologyStrategy.INTERSECTION_OF_SOME_MCS,
                "Intersection of all maximal consistent set", RefOntologyStrategy.INTERSECTION_OF_MCS,
                "Largest maximal consistent set", RefOntologyStrategy.LARGEST_MCS);
        refOntologySource = goals.get(refOntology.getItemAt(refOntology.getSelectedIndex()));
        refOntology.addActionListener(e -> {
            refOntologySource = goals.get(refOntology.getItemAt(refOntology.getSelectedIndex()));
        });
        refOntologyPanel.add(refOntology);
        add(refOntologyPanel);

        var basicCache = new JCheckBox("Basic cache");
        var uncachedCovers = new JCheckBox("Uncached covers");
        uncachedCovers.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_UNCACHED, uncachedCovers.isSelected());
            basicCache.setEnabled(!uncachedCovers.isSelected());
        });
        add(uncachedCovers);

        basicCache.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_BASIC_CACHED, basicCache.isSelected());
        });
        add(basicCache);

        var nnfStrict = new JCheckBox("Strict NNF");
        nnfStrict.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_NNF_STRICT, nnfStrict.isSelected());
        });
        add(nnfStrict);

        var sroiqStrict = new JCheckBox("Strict SROIQ");
        var alcStrict = new JCheckBox("Strict ALC");
        alcStrict.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_ALC_STRICT, alcStrict.isSelected());
            sroiqStrict.setEnabled(!alcStrict.isSelected());
        });
        add(alcStrict);

        sroiqStrict.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_SROIQ_STRICT, sroiqStrict.isSelected());
        });
        add(sroiqStrict);

        var simpleRiaWeakening = new JCheckBox("Basic RIA weakening");
        var simpleRolesStrict = new JCheckBox("Strict simple roles");
        simpleRolesStrict.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_SIMPLE_ROLES_STRICT, simpleRolesStrict.isSelected());
            simpleRiaWeakening.setEnabled(!simpleRolesStrict.isSelected());
        });
        add(simpleRolesStrict);

        simpleRiaWeakening.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_RIA_ONLY_SIMPLE, simpleRiaWeakening.isSelected());
        });
        add(simpleRiaWeakening);

        var noRoleRefinement = new JCheckBox("No role refinement");
        noRoleRefinement.addActionListener(e -> {
            setFlag(AxiomWeakener.FLAG_NO_ROLE_REFINEMENT, noRoleRefinement.isSelected());
            simpleRolesStrict.setEnabled(!noRoleRefinement.isSelected());
            simpleRiaWeakening.setEnabled(!noRoleRefinement.isSelected());
        });
        add(noRoleRefinement);

        var enhanceRefOnto = new JCheckBox("Enhance reference ontology");
        enhanceRefOnto.addActionListener(e -> {
            enhanceRef = enhanceRefOnto.isSelected();
        });
        add(enhanceRefOnto);
    }

    @Override
    public OntologyRepair getRepair(Predicate<Ontology> isRepaired) {
        return new OntologyRepairWeakening(isRepaired, refOntologySource, badAxiomSource, weakeningFlags, enhanceRef);
    }
}
