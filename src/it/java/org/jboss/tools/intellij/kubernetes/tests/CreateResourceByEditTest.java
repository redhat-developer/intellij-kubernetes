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
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.List;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

/**
 * @author olkornii@redhat.com
 */
public class CreateResourceByEditTest extends AbstractKubernetesTest{

    private static final String newResourceName = "newresourcename1";

    public static void createResourceByEdit(RemoteRobot robot, ComponentFixture kubernetesViewTree){
        openResourceContentList(new String[]{"Nodes"}, kubernetesViewTree);
        RemoteText selectedResource = getResourceByIdInParent("Nodes", 0, kubernetesViewTree);
        selectedResource.doubleClick();

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
        Keyboard myKeyboard = new Keyboard(robot);
        String newEditorTitle = newResourceName;

        Clipboard clipboard = getSystemClipboard();
        String text = "\"" + newResourceName + "\"";
        clipboard.setContents(new StringSelection(text), null);

        RemoteText namePlace = findResourceNamePosition(robot, editorSplitter, myKeyboard);
        namePlace.click(MouseButton.RIGHT_BUTTON);
        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Paste");

        ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
        toolbarMenu.PushToCluster();

        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "New resource was not been created.", () -> isResourceCreated(kubernetesViewTree, newResourceName, true)); // wait 15 seconds for Nodes load

        editorSplitter.closeEditor(newEditorTitle); // close editor
        hideClusterContent(kubernetesViewTree);
    }

    public static void deleteResource(RemoteRobot robot, ComponentFixture kubernetesViewTree){
        openResourceContentList(new String[]{"Nodes"}, kubernetesViewTree);
        kubernetesViewTree.findText(newResourceName).click(MouseButton.RIGHT_BUTTON);
        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Delete"); // delete the resource

        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Delete dialog did not appear.", () -> acceptDeleteDialog(robot));
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "New resource was not been deleted.", () -> isResourceDeleted(kubernetesViewTree, newResourceName));
        hideClusterContent(kubernetesViewTree);
    }

    private static RemoteText findResourceNamePosition(RemoteRobot robot, EditorsSplittersFixture editorSplitter, Keyboard myKeyboard){
        myKeyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_F);
        robot.find(ComponentFixture.class, byXpath("//div[@class='SearchTextArea']")).click();
        myKeyboard.enterText(" name:");


        ComponentFixture textFixture = editorSplitter.getEditorTextFixture();
        List<RemoteText> remoteText = textFixture.findAllText();

        int nameId = 0;
        for (RemoteText actual_remote_text : remoteText){
            if ("name".equals(actual_remote_text.getText())){
                break;
            }
            nameId++;
        }

        RemoteText namePlace = remoteText.get(nameId+3); // +1 because we need the next one, +1 because between every 2 real elements is space, +1 because here is the ":"
        namePlace.doubleClick(); // set the cursor

        return namePlace;
    }

    private static boolean acceptDeleteDialog(RemoteRobot robot){
        try {
            robot.find(ComponentFixture.class, byXpath("//div[@text='Yes']")).click();
            return true;
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
    }

    private static Clipboard getSystemClipboard()
    {
        Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        Clipboard systemClipboard = defaultToolkit.getSystemClipboard();

        return systemClipboard;
    }
}
