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
import com.redhat.devtools.intellij.commonuitest.UITestRunner;
import com.redhat.devtools.intellij.commonuitest.fixtures.dialogs.FlatWelcomeFrame;
import com.redhat.devtools.intellij.commonuitest.fixtures.dialogs.information.TipDialog;
import com.redhat.devtools.intellij.commonuitest.utils.runner.IntelliJVersion;
import com.redhat.devtools.intellij.commonuitest.fixtures.dialogs.project.NewProjectDialogWizard;
import com.redhat.devtools.intellij.commonuitest.fixtures.mainidewindow.idestatusbar.IdeStatusBar;
import com.redhat.devtools.intellij.commonuitest.fixtures.mainidewindow.toolwindowspane.ToolWindowPane;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.ProjectStructureDialog;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.KubernetesToolsFixture;
import static com.intellij.remoterobot.search.locators.Locators.byXpath;

import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.SingleHeighLabelFixture;
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
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * JUnit UI tests for intellij-kubernetes
 *
 * @author olkornii@redhat.com
 */
public class BasicTests {

    private static RemoteRobot robot;
    private static ComponentFixture kubernetesViewTree;
    private static final Logger LOGGER = Logger.getLogger(BasicTests.class.getName());

    @BeforeAll
    public static void connect() {
        robot = UITestRunner.runIde(IntelliJVersion.COMMUNITY_V_2022_3, 8580);
        createEmptyProject();
        openKubernetesTab();

        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class, Duration.ofSeconds(5));
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
        final NewProjectDialogWizard newProjectDialogWizard = flatWelcomeFrame.find(NewProjectDialogWizard.class, Duration.ofSeconds(20));
        selectNewProjectType("Empty Project");
        newProjectDialogWizard.finish();

        final IdeStatusBar ideStatusBar = robot.find(IdeStatusBar.class, Duration.ofSeconds(10));
        ideStatusBar.waitUntilProjectImportIsComplete();
        ProjectStructureDialog.cancelProjectStructureDialogIfItAppears(robot);
        closeTipDialogIfItAppears();
        closeGotItPopup();
        closeOpenedEditors();
        ideStatusBar.waitUntilAllBgTasksFinish();
    }

    private static void openKubernetesTab(){
        final ToolWindowPane toolWindowPane = robot.find(ToolWindowPane.class, Duration.ofSeconds(5));
        toolWindowPane.stripeButton("Kubernetes", false).click();
    }

    private static boolean isKubernetesViewTreeAvailable(){
        List<RemoteText> allText = kubernetesViewTree.findAllText();
        String firstText = allText.get(0).getText();
        return !"Nothing to show".equals(firstText);
    }

    private static void closeTipDialogIfItAppears() {
        try {
            TipDialog tipDialog = robot.find(TipDialog.class, Duration.ofSeconds(10));
            tipDialog.close();
        } catch (WaitForConditionTimeoutException e) {
            e.printStackTrace();
        }
    }

    private static void closeGotItPopup() {
        try {
            robot.find(ComponentFixture.class, byXpath("JBList", "//div[@accessiblename='Got It' and @class='JButton' and @text='Got It']"), Duration.ofSeconds(10)).click();
        } catch (WaitForConditionTimeoutException e) {
            e.printStackTrace();
        }
    }

    private static void closeOpenedEditors() {
        List<SingleHeighLabelFixture> singleHeighLabelsList = robot.findAll(SingleHeighLabelFixture.class, byXpath("//div[@class='SingleHeightLabel']"));
        LOGGER.log(Level.INFO, "Next opened editors will be closed: " + singleHeighLabelsList);
        for (SingleHeighLabelFixture singleHeighLabel : singleHeighLabelsList) {
            singleHeighLabel.find(ComponentFixture.class, byXpath("//div[@accessiblename='Close. Alt-Click to Close Others (Ctrl+F4)' and @class='InplaceButton']")).click();
        }
    }

    private static void selectNewProjectType(String projectType) {
        ComponentFixture newProjectTypeList = robot.findAll(ComponentFixture.class, byXpath("JBList", "//div[@class='JBList']")).get(0);
        newProjectTypeList.findText(projectType).click();
    }

}