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
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.assertj.swing.core.MouseButton;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.NewProjectDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.WelcomeFrameDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.IdeStatusBarFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.KubernetesToolsFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.ToolWindowsPaneFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;
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
        createEmptyProject();
        openKubernetesTab();
        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class);
        kubernetesViewTree = kubernetesToolsFixture.getKubernetesViewTree();
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", BasicTests::isKubernetesViewTreeAvailable);
    }

    @Test
    public void checkClusterConnected() {
        step("New Empty Project", () -> {
            String clusterText = kubernetesViewTree.findAllText().get(0).getText();
            assertTrue(clusterText.contains("minikube"));
        });
    }

    @Test
    public void openResourceEditor() {
        step("open Resource Editor", () -> {
            openNodesList();

            RemoteText selectedResource = getResourceByIdInParent("Nodes", 0); // get the resource with id 0
            selectedResource.click(MouseButton.RIGHT_BUTTON); // select the resource
            RightClickMenu rightClickMenu = robot.find(RightClickMenu.class); // open the yml editor
            rightClickMenu.select("Edit..."); // open the yml editor

            EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
            String editorTitle = selectedResource.getText() + ".yml";
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Editor is not available.", () -> isEditorOpened(editorTitle)); // wait 15 seconds for editor
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Resource schema is wrong.", () -> isSchemaSet("v1#Node")); // wait 15 seconds for set right schema

            editorSplitter.closeEditor(editorTitle);
            hideClusterContent();
        });
    }

    @Test
    public void editResource() {
        step("edit Resource", () -> {
            openNodesList();

            RemoteText selectedResource = getResourceByIdInParent("Nodes", 0);
            selectedResource.doubleClick();

            EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
            String editorTitle = selectedResource.getText() + ".yml";

            ComponentFixture textFixture = editorSplitter.getEditorTextFixture(editorTitle);
            List<RemoteText> remote_text = textFixture.findAllText();
            int labelsId = 0;
            for (RemoteText actual_remote_text : remote_text){
                if ("labels".equals(actual_remote_text.getText())){
                    break;
                }
                labelsId++;
            }
            RemoteText placeForNewLabel = remote_text.get(labelsId+2); // +1 because we need the next one, +1 because between every 2 real elements is space
            placeForNewLabel.click(); // set the cursor
            Keyboard my_keyboard = new Keyboard(robot);
            my_keyboard.enterText("    some_label: \"some_label\"");
            my_keyboard.enter();
            my_keyboard.backspace();

            ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
            toolbarMenu.PushToCluster();

            editorSplitter.closeEditor(editorTitle);
            hideClusterContent();

            openNodesList();

            selectedResource.doubleClick();
            ComponentFixture textFixtureNew = editorSplitter.getEditorTextFixture(editorTitle);
            List<RemoteText> remoteTextNew = textFixtureNew.findAllText();
            boolean labelExist = false;
            for (RemoteText actual_remote_text : remoteTextNew){
                if (actual_remote_text.getText().contains("some_label")){
                    labelExist = true;
                    break;
                }
            }

            editorSplitter.closeEditor(editorTitle);
            hideClusterContent();

            assertTrue(labelExist);
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

    private static RemoteText getResourceByIdInParent(String parentName, int id){
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

    private static void openClusterContent(){
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
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", BasicTests::isNodesOpened);
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

    private static boolean isKubernetesViewTreeAvailable(){
        List<RemoteText> allText = kubernetesViewTree.findAllText();
        String firstText = allText.get(0).getText();
        return !"Nothing to show".equals(firstText);
    }

    private static boolean isNodesOpened(){
        List<RemoteText> allTextFromTree = kubernetesViewTree.findAllText();
        for (RemoteText actualText : allTextFromTree){
            if (actualText.getText().contains("Nodes")){
                return true;
            }
        }
        return false;
    }

    private static boolean isNodesLoaded(){
        List<RemoteText> allTextFromTree = kubernetesViewTree.findAllText();
        for (RemoteText actualText : allTextFromTree){
            if (actualText.getText().contains("loading...")){
                return false;
            }
        }
        return true;
    }

    private static void openNodesList(){
        openClusterContent();
        kubernetesViewTree.findText("Nodes").doubleClick(MouseButton.LEFT_BUTTON); // open Nodes content
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Nodes is not available.", BasicTests::isNodesLoaded);
    }

    private static void hideClusterContent(){
        String clusterText = kubernetesViewTree.findAllText().get(0).getText();
        kubernetesViewTree.findText(clusterText).doubleClick(); // hide cluster content
    }
}