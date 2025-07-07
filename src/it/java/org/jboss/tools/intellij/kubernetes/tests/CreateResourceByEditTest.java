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
package org.jboss.tools.intellij.kubernetes.tests;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.dataExtractor.RemoteText;
import com.intellij.remoterobot.utils.Keyboard;
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.assertj.swing.core.MouseButton;
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olkornii@redhat.com
 */
public class CreateResourceByEditTest extends AbstractKubernetesTest{

    private static final String newResourceName = "newresourcename1";

    public CreateResourceByEditTest(String clusterName, ComponentFixture kubernetesViewTree, RemoteRobot remoteRobot) {
        super(clusterName, kubernetesViewTree, remoteRobot);
    }

    public void createResourceByEdit(){
        getFirstResourceInNodes().doubleClick(MouseButton.LEFT_BUTTON);

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
        Keyboard myKeyboard = new Keyboard(robot);

        String text = "\"" + newResourceName;

        RemoteText namePlace = findResourceNamePosition(editorSplitter);
        namePlace.doubleClick(MouseButton.LEFT_BUTTON);
        myKeyboard.enterText(text); // replace with new name

        ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
        toolbarMenu.pushToCluster();

        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "New resource was not been created.", () -> isResourceCreated(newResourceName, true)); // wait 15 seconds for Nodes load

        editorSplitter.closeEditor(); // close editor
        hideClusterContent();
    }

    public void deleteResource(){
        kubernetesViewTree.findText(clusterName).rightClick();
        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Refresh");
        RemoteText toDelete = getNamedResourceInNodes(newResourceName);
        toDelete.click(MouseButton.RIGHT_BUTTON);
        rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Delete"); // delete the resource

        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Delete dialog did not appear.", this::acceptDeleteDialog);
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "New resource was not been deleted.", () -> isResourceDeleted(newResourceName));
        hideClusterContent();
    }

    private RemoteText findResourceNamePosition(EditorsSplittersFixture editorSplitter){
        scrollToVisible(" name:");

        ComponentFixture textFixture = editorSplitter.getEditorTextFixture();

        List<RemoteText> allVisibleElements = textFixture.findAllText();
        Optional<RemoteText> resourceText = allVisibleElements.stream().filter(remoteText -> remoteText.getText().equals("name")).findFirst();
        assertTrue(resourceText.isPresent());
        return allVisibleElements.get(allVisibleElements.indexOf(resourceText.get())+3); // +1 because we need the next one, +1 because between every 2 real elements is space, +1 because here is the ":"
    }

    private boolean acceptDeleteDialog(){
        try {
            robot.find(ComponentFixture.class, byXpath("//div[@text='Yes']")).click();
            return true;
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
    }
}
