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
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.NewProjectDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.WelcomeFrameDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.KubernetesToolsFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.ToolWindowsPaneFixture;
import org.jboss.tools.intellij.kubernetes.tests.*;
import org.jboss.tools.intellij.kubernetes.utils.GlobalUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

import java.time.Duration;
import java.util.List;

/**
 * JUnit UI tests for intellij-kubernetes
 *
 * @author olkornii@redhat.com
 */
public class BasicTests extends AbstractKubernetesTest{

    private static RemoteRobot robot;
    private static ComponentFixture kubernetesViewTree;

    @BeforeAll
    public static void connect() throws InterruptedException {
        GlobalUtils.waitUntilIntelliJStarts(8082);
        robot = GlobalUtils.getRemoteRobotConnection(8082);
        GlobalUtils.clearTheWorkspace(robot);
        createEmptyProject();
        openKubernetesTab();
        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class);
        kubernetesViewTree = kubernetesToolsFixture.getKubernetesViewTree();
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", BasicTests::isKubernetesViewTreeAvailable);
    }

    @Test
    public void checkClusterConnected() {
        step("New Empty Project", () -> ClusterConnectedTest.checkClusterConnected(robot, kubernetesViewTree));
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

    private static final String newResourceName = "newresourcename2";

    @Test
    public void createAnotherResourceTypeByEdit() {
        step("create another type of Resource", () -> CreateAnotherTypeResourceByEditTest.createAnotherTypeResourceByEdit(robot, kubernetesViewTree));
    }


    private static void createEmptyProject(){
        final WelcomeFrameDialogFixture welcomeFrameDialogFixture = robot.find(WelcomeFrameDialogFixture.class);
        welcomeFrameDialogFixture.createNewProjectLink().click();
        final NewProjectDialogFixture newProjectDialogFixture = welcomeFrameDialogFixture.find(NewProjectDialogFixture.class, Duration.ofSeconds(20));
        newProjectDialogFixture.projectTypeJBList().findText("Empty Project").click();
        newProjectDialogFixture.button("Next").click();
        newProjectDialogFixture.button("Finish").click();
        GlobalUtils.waitUntilTheProjectImportIsComplete(robot);
        GlobalUtils.cancelProjectStructureDialogIfItAppears(robot);
        GlobalUtils.closeTheTipOfTheDayDialogIfItAppears(robot);
        GlobalUtils.waitUntilAllTheBgTasksFinish(robot);
    }

    private static void openKubernetesTab(){
        final ToolWindowsPaneFixture toolWindowsPaneFixture = robot.find(ToolWindowsPaneFixture.class);
        waitFor(Duration.ofSeconds(10), Duration.ofSeconds(1), "The 'Kubernetes' stripe button is not available.", () -> isStripeButtonAvailable(toolWindowsPaneFixture, "Kubernetes"));
        toolWindowsPaneFixture.stripeButton("Kubernetes").click();
    }

    private static boolean isStripeButtonAvailable(ToolWindowsPaneFixture toolWindowsPaneFixture, String label) { // loading...
        try {
            toolWindowsPaneFixture.stripeButton(label);
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