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
import org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olkornii@redhat.com
 */
public class EditResourceTest extends AbstractKubernetesTest{
    public static void editResource(RemoteRobot robot, ComponentFixture kubernetesViewTree){
        String yodaLabel = "yoda_label";
        String yodaText = "yoda_text";

        openResourceContentList(new String[]{"Nodes"}, kubernetesViewTree);
        RemoteText selectedResource = getResourceByIdInParent("Nodes", 0, kubernetesViewTree);
        selectedResource.doubleClick();

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);
        String editorTitle = selectedResource.getText();

        RemoteText placeForNewLabel = getPlaceForNewLabel(editorSplitter);
        placeForNewLabel.click();

        String text = "    " + yodaLabel +": \"" + yodaText;
        Keyboard my_keyboard = new Keyboard(robot);

        my_keyboard.enter(); // create empty line
        placeForNewLabel.click(); // focus on the empty line
        my_keyboard.enterText(text); // enter text

        ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
        toolbarMenu.PushToCluster();

        editorSplitter.closeEditor(editorTitle);
        hideClusterContent(kubernetesViewTree);

        openResourceContentList(new String[]{"Nodes"}, kubernetesViewTree);

        selectedResource.doubleClick();
        ComponentFixture textFixtureNew = editorSplitter.getEditorTextFixture();
        scrollToVisible(yodaLabel, robot);
        List<RemoteText> remoteTextNew = textFixtureNew.findAllText();
        boolean labelExist = false;
        for (RemoteText actual_remote_text : remoteTextNew){
            if (actual_remote_text.getText().contains(yodaText)){
                labelExist = true;
                break;
            }
        }

        editorSplitter.closeEditor(editorTitle); // close editor
        hideClusterContent(kubernetesViewTree);

        assertTrue(labelExist, "Label '" + yodaLabel + "' not found.");
    }

    private static RemoteText getPlaceForNewLabel (EditorsSplittersFixture editorSplitter) {
        ComponentFixture textFixture = editorSplitter.getEditorTextFixture();
        List<RemoteText> remote_text = textFixture.findAllText();

        int labelsId = 0;
        boolean labelsFound = false;
        for (RemoteText actual_remote_text : remote_text){
            if ("labels".equals(actual_remote_text.getText())){
                labelsFound = true;
                break;
            }
            labelsId++;
        }

        assertTrue(labelsFound, "Labels not found in resource.");

        return remote_text.get(labelsId+2); // +1 because we need the next one, +1 because between every 2 real elements is space
    }
}
