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
import org.assertj.swing.core.MouseButton;
import org.jboss.tools.intellij.kubernetes.fixtures.dialogs.IdeFatalErrorsDialogFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.IdeStatusBarFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.time.Duration;

import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author olkornii@redhat.com
 */
public class CreateAnotherTypeResourceByEditTest extends AbstractKubernetesTest{

    private static final String newResourceName = "newresourcename2";

    public static void createAnotherTypeResourceByEdit(RemoteRobot robot, ComponentFixture kubernetesViewTree){
        clearErrors(robot);

        openResourceContentList(new String[]{"Nodes"}, kubernetesViewTree);
        RemoteText selectedResource = getResourceByIdInParent("Nodes", 0, kubernetesViewTree);
        selectedResource.doubleClick();

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
        Keyboard myKeyboard = new Keyboard(robot);

        setupNewPod(robot, myKeyboard);

        clearErrors(robot);

        ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
        toolbarMenu.PushToCluster();

        checkErrors(robot);

        editorSplitter.closeEditor(newResourceName); // close editor
        hideClusterContent(kubernetesViewTree);
        openResourceContentList(new String[] {"Workloads", "Pods"}, kubernetesViewTree);
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "New resource was not been created.", () -> isResourceCreated(kubernetesViewTree, newResourceName, false));
        hideClusterContent(kubernetesViewTree);
    }

    private static void setupNewPod(RemoteRobot robot, Keyboard myKeyboard){
        Clipboard clipboard = getSystemClipboard();

        String text = "apiVersion: apps/v1\n" +
                "kind: Deployment\n" +
                "metadata:\n" +
                "  name: " + newResourceName + "\n" +
                "spec:\n" +
                "  replicas: 2\n" +
                "  selector:\n" +
                "    matchLabels:\n" +
                "      app: sise\n" +
                "  template:\n" +
                "    metadata:\n" +
                "      labels:\n" +
                "        app: sise\n" +
                "    spec:\n" +
                "      containers:\n" +
                "      - name: sise\n" +
                "        image: quay.io/openshiftlabs/simpleservice:0.5.0\n" +
                "        ports:\n" +
                "        - containerPort: 9876\n" +
                "        env:\n" +
                "        - name: SIMPLE_SERVICE_VERSION\n" +
                "          value: \"0.9\"";

        clipboard.setContents(new StringSelection(text), null);

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
        ComponentFixture textFixture = editorSplitter.getEditorTextFixture();
        RemoteText remoteText = textFixture.findAllText().get(0);

        myKeyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        remoteText.click(MouseButton.RIGHT_BUTTON);

        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Paste");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void checkErrors(RemoteRobot robot){
        String errorMessage = "";
        boolean isErrorAfterPush = isError(robot);
        if (isErrorAfterPush){
            robot.find(IdeStatusBarFixture.class).ideErrorsIcon().click();
            IdeFatalErrorsDialogFixture ideErrorsDialog = robot.find(IdeFatalErrorsDialogFixture.class);
            for (RemoteText remoteText: ideErrorsDialog.exceptionDescriptionJTextArea().findAllText()){
                errorMessage = errorMessage + remoteText.getText();
            }
        }
        assertFalse(isErrorAfterPush, errorMessage);
    }
}
