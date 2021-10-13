/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import com.redhat.devtools.intellij.commonUiTestLibrary.UITestRunner;
import com.redhat.devtools.intellij.commonUiTestLibrary.fixtures.dialogs.FlatWelcomeFrame;
import com.redhat.devtools.intellij.commonUiTestLibrary.fixtures.dialogs.information.TipDialog;
import com.redhat.devtools.intellij.commonUiTestLibrary.fixtures.dialogs.projectManipulation.NewProjectDialog;
import com.redhat.devtools.intellij.commonUiTestLibrary.fixtures.mainIdeWindow.ideStatusBar.IdeStatusBar;
import com.redhat.devtools.intellij.commonUiTestLibrary.fixtures.mainIdeWindow.toolWindowsPane.ToolWindowsPane;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.ProjectStructureDialog;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.KubernetesToolsFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

import org.jboss.tools.intellij.kubernetes.tests.ClusterConnectedTest;
import org.jboss.tools.intellij.kubernetes.tests.CreateResourceByEditTest;
import org.jboss.tools.intellij.kubernetes.tests.OpenResourceEditorTest;
import org.jboss.tools.intellij.kubernetes.tests.EditResourceTest;
import org.jboss.tools.intellij.kubernetes.tests.CreateAnotherTypeResourceByEditTest;

import java.time.Duration;
import java.util.List;

/**
 * JUnit UI tests for intellij-kubernetes
 *
 * @author olkornii@redhat.com
 */
public class BasicTests {

    private static RemoteRobot robot;
    private static ComponentFixture kubernetesViewTree;

    @BeforeAll
    public static void connect() {
        robot = UITestRunner.runIde(UITestRunner.IdeaVersion.V_2020_2, 8580);
        createEmptyProject();
        openKubernetesTab();
        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class);
        kubernetesViewTree = kubernetesToolsFixture.getKubernetesViewTree();
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", BasicTests::isKubernetesViewTreeAvailable);
    }

    @AfterAll
    public static void closeIde() {
        UITestRunner.closeIde();
    }

    @Test
    public void checkClusterConnected() {
        step("New Empty Project", () -> ClusterConnectedTest.checkClusterConnected(kubernetesViewTree));
    }

    @Test
    public void openResourceEditor() {
        step("open Resource Editor", () -> OpenResourceEditorTest.checkResourceEditor(robot, kubernetesViewTree));
    }

    @Test
    public void editResource() {
        step("edit Resource", () -> EditResourceTest.editResource(robot, kubernetesViewTree));
    }

    @Test
    public void createResourceByEdit() {
        step("create Resource", () -> CreateResourceByEditTest.createResourceByEdit(robot, kubernetesViewTree));

        step("delete Resource", () -> CreateResourceByEditTest.deleteResource(robot, kubernetesViewTree));
    }

//    @Test
    public void createAnotherResourceTypeByEdit() {
        step("create another type of Resource", () -> CreateAnotherTypeResourceByEditTest.createAnotherTypeResourceByEdit(robot, kubernetesViewTree));
    }


    private static void createEmptyProject(){
        final FlatWelcomeFrame flatWelcomeFrame = robot.find(FlatWelcomeFrame.class);
        flatWelcomeFrame.createNewProject();
        final NewProjectDialog newProjectDialogFixture = flatWelcomeFrame.find(NewProjectDialog.class, Duration.ofSeconds(20));
        newProjectDialogFixture.selectNewProjectType("Empty Project");
        newProjectDialogFixture.next();
        newProjectDialogFixture.finish();

        final IdeStatusBar ideStatusBar = robot.find(IdeStatusBar.class);
        ideStatusBar.waitUntilProjectImportIsComplete();
        ProjectStructureDialog.cancelProjectStructureDialogIfItAppears(robot);
        TipDialog.closeTipDialogIfItAppears(robot);
        ideStatusBar.waitUntilAllBgTasksFinish();
    }

    private static void openKubernetesTab(){
        final ToolWindowsPane toolWindowsPane = robot.find(ToolWindowsPane.class);
        waitFor(Duration.ofSeconds(10), Duration.ofSeconds(1), "The 'Kubernetes' stripe button is not available.", () -> isStripeButtonAvailable(toolWindowsPane, "Kubernetes"));
        toolWindowsPane.stripeButton("Kubernetes").click();
    }

    private static boolean isStripeButtonAvailable(ToolWindowsPane toolWindowsPane, String label) { // loading...
        try {
            toolWindowsPane.stripeButton(label);
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
        return true;
    }

    private static boolean isKubernetesViewTreeAvailable(){
        List<RemoteText> allText = kubernetesViewTree.findAllText();
        String firstText = allText.get(0).getText();
        return !"Nothing to show".equals(firstText);
    }

}