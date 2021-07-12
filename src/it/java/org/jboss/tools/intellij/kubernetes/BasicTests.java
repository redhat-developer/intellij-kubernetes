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
import com.intellij.remoterobot.utils.Keyboard;

import com.intellij.remoterobot.data.TextData;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.assertj.swing.core.MouseButton;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.NewProjectDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.WelcomeFrameDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.IdeStatusBarFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.KubernetesToolsFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.ToolWindowsPaneFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;
import org.jboss.tools.intellij.kubernetes.utils.GlobalUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

/**
 * Basic JUnit UI tests for intellij-kubernetes
 *
 * @author olkornii@redhat.com
 */
public class BasicTests {

    private static RemoteRobot robot;
    private static ComponentFixture kubernetesViewTree;

    @BeforeAll
    public static void connect() throws InterruptedException {
        GlobalUtils.waitUntilIntelliJStarts(8082);
        robot = GlobalUtils.getRemoteRobotConnection(8082);
        GlobalUtils.clearTheWorkspace(robot);
        createEpmtyProject();
        openKubernetesTab();
        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class);
        kubernetesViewTree = kubernetesToolsFixture.getKubernetesViewTree();
    }

    @Test
    public void checkClusterConnected() {
        step("New Empty Project", () -> {
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", () -> isKubernetesViewTreeAvailable(kubernetesViewTree));
            String clusterText = kubernetesViewTree.findAllText().get(0).getText();
            assertTrue(clusterText.contains("minikube"));
        });
    }

    @Test
    public void openResourceEditor() {
        step("open Resource Editor", () -> {
            openClusterContent(kubernetesViewTree);
            kubernetesViewTree.findText("Nodes").doubleClick(MouseButton.LEFT_BUTTON); // open Nodes content
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Nodes is not available.", () -> isNodesLoaded(kubernetesViewTree));

            RemoteText selectedResource = getResourceByIdInParent("Nodes", 0, kubernetesViewTree); // get the resource with id 0
            selectedResource.click(MouseButton.RIGHT_BUTTON); // select the resource
            RightClickMenu rightClickMenu = robot.find(RightClickMenu.class); // open the yml editor
            rightClickMenu.select("Edit..."); // open the yml editor

            EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
            String editorTitle = selectedResource.getText() + ".yml";
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Editor is not available.", () -> isEditorOpened(editorTitle)); // wait 15 secods for editor
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Resource schema is wrong.", () -> isSchemaSet("v1#Node")); // wait 15 seconds for set right schema

            editorSplitter.closeEditor(editorTitle); // close editor
            String clusterText = kubernetesViewTree.findAllText().get(0).getText();
            kubernetesViewTree.findText(clusterText).doubleClick(); // hide cluster content
        });
    }

    private static boolean isSchemaSet(String schemaName){
        try {
            IdeStatusBarFixture statusBarFixture = robot.find(IdeStatusBarFixture.class);
            statusBarFixture.withIconAndArrows("Schema: " + schemaName);
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
        return true;
    }

    private static RemoteText getResourceByIdInParent(String parentName, int id, ComponentFixture kubernetesViewTree){
        List<RemoteText> kubernetesToolsText = kubernetesViewTree.findAllText();
        int parentId = 0;
        for (RemoteText findParent : kubernetesToolsText){
            if (findParent.getText().contains(parentName)){
                break;
            }
            parentId++;
        }
        return kubernetesViewTree.findAllText().get(parentId + id + 1);
    }

    private static void openClusterContent(ComponentFixture kubernetesViewTree){
        List<RemoteText> kubernetesToolsText = kubernetesViewTree.findAllText();
        boolean needClickOnMinikube = true;
        for (RemoteText findNodes : kubernetesToolsText){
            if (findNodes.getText().contains("Nodes")){
                needClickOnMinikube = false;
                break;
            }
        }
        if (needClickOnMinikube){
            String clusterText = kubernetesViewTree.findAllText().get(0).getText();
            kubernetesViewTree.findText(clusterText).doubleClick(MouseButton.LEFT_BUTTON);
        }
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", () -> isNodesOpened(kubernetesViewTree));
    }

    private static void createEpmtyProject(){
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

    private static boolean isEditorOpened(String editorTitle){
        try {
            robot.find(EditorsSplittersFixture.class, byXpath("//div[@accessiblename='Editor for " + editorTitle + "' and @class='EditorComponentImpl']"));
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
        return true;
    }

    private static boolean isStripeButtonAvailable(ToolWindowsPaneFixture toolWindowsPaneFixture, String label) { // loading...
        try {
            toolWindowsPaneFixture.stripeButton(label);
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
        return true;
    }

    private static boolean isKubernetesViewTreeAvailable(ComponentFixture kubernetesViewTree){
        List<RemoteText> allText = kubernetesViewTree.findAllText();
        String firstText = allText.get(0).getText();
        if("Nothing to show".equals(firstText)){
            return false;
        }
        return true;
    }

    private static boolean isNodesOpened(ComponentFixture kubernetesViewTree){
        List<RemoteText> allTextFromTree = kubernetesViewTree.findAllText();
        for (RemoteText actualText : allTextFromTree){
            if (actualText.getText().contains("Nodes")){
                return true;
            }
        }
        return false;
    }

    private static boolean isNodesLoaded(ComponentFixture kubernetesViewTree){
        List<RemoteText> allTextFromTree = kubernetesViewTree.findAllText();
        for (RemoteText actualText : allTextFromTree){
            if (actualText.getText().contains("loading...")){
                return false;
            }
        }
        return true;
    }
}