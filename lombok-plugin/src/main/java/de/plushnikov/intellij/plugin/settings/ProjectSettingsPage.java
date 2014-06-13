package de.plushnikov.intellij.plugin.settings;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 *
 */
public class ProjectSettingsPage implements SearchableConfigurable, Configurable.NoScroll {
  public static final String JAVAC_COMPILER_ID = "Javac";

  private JPanel myPanel = new JPanel(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
  private JCheckBox myEnableLombokInProject = new JCheckBox("Enable Lombok support for this project ");
  private JLabel myAnnotationConfigurationInfo1Label = new JLabel("Lombok plugin only works with Javac compiler. ");
  private JLabel myAnnotationConfigurationInfo2Label = new JLabel("You should activate external compiler option and enable annotation processors");
  private JLabel myAnnotationConfigurationInfo3Label = new JLabel("or disable external compiler and disable all of annotation compilers to work with lombok.");
  private JLabel myAnnotationConfigurationOkLabel = new JLabel("Configuration of IntelliJ seems to be ok");
  private JButton checkButton = new JButton("Verify IntelliJ configuration for Lombok support");

  private Project myProject;

  public ProjectSettingsPage(Project project) {
    myProject = project;

    JPanel panel1 = new JPanel(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myEnableLombokInProject.setSelected(false);
    myEnableLombokInProject.setToolTipText("");
    panel1.add(myEnableLombokInProject, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    panel1.add(new JLabel("(You may be required to reopen your project to take this change correctly applied.)"),
        new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    panel1.add(new Spacer(),
        new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 10), null, null, 0, false));

    JPanel panel2 = new JPanel(new GridLayoutManager(5, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel.add(panel2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myAnnotationConfigurationInfo1Label.setFont(new Font(myAnnotationConfigurationInfo1Label.getFont().getName(), Font.BOLD, myAnnotationConfigurationInfo1Label.getFont().getSize()));
    myAnnotationConfigurationInfo1Label.setForeground(Color.RED);
    panel2.add(myAnnotationConfigurationInfo1Label,
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    myAnnotationConfigurationInfo2Label.setFont(new Font(myAnnotationConfigurationInfo2Label.getFont().getName(), Font.BOLD, myAnnotationConfigurationInfo2Label.getFont().getSize()));
    myAnnotationConfigurationInfo2Label.setForeground(Color.RED);
    panel2.add(myAnnotationConfigurationInfo2Label, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    panel2.add(checkButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    myAnnotationConfigurationOkLabel.setFont(new Font(myAnnotationConfigurationOkLabel.getFont().getName(), Font.BOLD, myAnnotationConfigurationOkLabel.getFont().getSize()));
    myAnnotationConfigurationOkLabel.setForeground(Color.GREEN);
    panel2.add(myAnnotationConfigurationOkLabel, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    myAnnotationConfigurationInfo3Label.setFont(new Font(myAnnotationConfigurationInfo3Label.getFont().getName(), Font.BOLD, myAnnotationConfigurationInfo3Label.getFont().getSize()));
    myAnnotationConfigurationInfo3Label.setForeground(Color.RED);
    panel2.add(myAnnotationConfigurationInfo3Label, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    myPanel.add(new Spacer(), new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));

    checkButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateAnnotationConfigurationInfo();
      }
    });
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Lombok plugin";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myEnableLombokInProject.setSelected(ProjectSettings.isEnabledInProject(myProject));
    updateAnnotationConfigurationInfo();

    return myPanel;
  }

  private void updateAnnotationConfigurationInfo() {
    boolean annotationProcessingPossible = isLombokAnnotationProcessingPossible();

    myAnnotationConfigurationOkLabel.setVisible(annotationProcessingPossible);
    myAnnotationConfigurationInfo1Label.setVisible(!annotationProcessingPossible);
    myAnnotationConfigurationInfo2Label.setVisible(!annotationProcessingPossible);
    myAnnotationConfigurationInfo3Label.setVisible(!annotationProcessingPossible);
  }

  private boolean isLombokAnnotationProcessingPossible() {
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
    boolean javacCompiler = JAVAC_COMPILER_ID.equals(((CompilerConfigurationImpl) compilerConfiguration).getDefaultCompiler().getId());
    boolean annotationProcessorsEnabled = compilerConfiguration.isAnnotationProcessorsEnabled();
    boolean externBuild = CompilerWorkspaceConfiguration.getInstance(myProject).useOutOfProcessBuild();

    return (externBuild && annotationProcessorsEnabled) || (!externBuild && !annotationProcessorsEnabled && javacCompiler);
  }

  @Override
  public boolean isModified() {
    return myEnableLombokInProject.isSelected() != ProjectSettings.isEnabledInProject(myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    ProjectSettings.setEnabledInProject(myProject, myEnableLombokInProject.isSelected());
  }

  @Override
  public void reset() {
    myEnableLombokInProject.setSelected(ProjectSettings.isEnabledInProject(myProject));
  }

  @Override
  public void disposeUIResources() {

  }

  @NotNull
  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }
}
