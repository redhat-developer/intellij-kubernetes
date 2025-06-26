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
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException;
import org.assertj.swing.core.MouseButton;
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.IdeStatusBarFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;

import java.time.Duration;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

/**
 * @author olkornii@redhat.com
 */
public class OpenResourceEditorTest extends AbstractKubernetesTest{

    public OpenResourceEditorTest(String clusterName, ComponentFixture kubernetesViewTree, RemoteRobot remoteRobot) {
        super(clusterName, kubernetesViewTree, remoteRobot);
    }

    public void checkResourceEditor(){
        RemoteText selectedResource = getNamedResourceInNodes(resourceName);
        selectedResource.click(MouseButton.RIGHT_BUTTON); // select the resource
        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class); // open the yml editor
        rightClickMenu.select("Edit..."); // open the yml editor

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Editor is not available.", this::isEditorOpened); // wait 15 seconds for editor
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Resource schema is wrong.", this::isSchemaSet); // wait 15 seconds for set right schema

        editorSplitter.closeEditor(); // close editor
        hideClusterContent();
    }

    private boolean isEditorOpened(){
        try {
            robot.find(ComponentFixture.class, byXpath("//div[@class='EditorComponentImpl']"));
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
        return true;
    }

    private boolean isSchemaSet(){
        try {
            IdeStatusBarFixture statusBarFixture = robot.find(IdeStatusBarFixture.class);
            statusBarFixture.withIconAndArrows("Schema: v1#Pod");
        } catch (WaitForConditionTimeoutException e) {
            return false;
        }
        return true;
    }
}
