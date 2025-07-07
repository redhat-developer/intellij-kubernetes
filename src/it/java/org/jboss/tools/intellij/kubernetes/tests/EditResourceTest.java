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
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Optional;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olkornii@redhat.com
 */
public class EditResourceTest extends AbstractKubernetesTest{

    public EditResourceTest(String clusterName, ComponentFixture kubernetesViewTree, RemoteRobot remoteRobot) {
        super(clusterName, kubernetesViewTree, remoteRobot);
    }

    public void editResource(){
        String yodaLabel = "yoda_label";
        String yodaText = "yoda_text";

        getNamedResourceInNodes("hello-minikube").doubleClick();

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);

        String text = yodaLabel +": \"" + yodaText;
        Keyboard myKeyboard = new Keyboard(robot);

        RemoteText placeForNewLabel = getPlaceForNewLabel(editorSplitter);
        placeForNewLabel.click();
        myKeyboard.key(KeyEvent.VK_RIGHT);
        myKeyboard.enter(); // create empty line
        myKeyboard.enterText(text); // enter text

        ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
        toolbarMenu.pushToCluster();

        editorSplitter.closeEditor();
        hideClusterContent();

        RemoteText resource = getNamedResourceInNodes("hello-minikube");
        resource.doubleClick();
        ComponentFixture textFixtureNew = editorSplitter.getEditorTextFixture();
        Optional<RemoteText> label = textFixtureNew.findAllText().stream().filter(remoteText -> remoteText.getText().contains(yodaText)).findFirst();
        assertTrue(
            label.isPresent(),
            "Label '" + yodaLabel + "' not found.");

        editorSplitter.closeEditor();

        //delete pod to refresh label
        resource.click(MouseButton.RIGHT_BUTTON);
        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Delete");
        robot.find(ComponentFixture.class, byXpath("//div[@text='Yes']")).click();

        hideClusterContent();
    }

    private RemoteText getPlaceForNewLabel (EditorsSplittersFixture editorSplitter) {
        ComponentFixture textFixture = editorSplitter.getEditorTextFixture();

        List<RemoteText> allVisibleElements = textFixture.findAllText();
        Optional<RemoteText> resourceText = allVisibleElements.stream().filter(remoteText -> remoteText.getText().equals("labels")).findFirst();
        assertTrue(resourceText.isPresent());

        return allVisibleElements.get(allVisibleElements.indexOf(resourceText.get())+1);
    }
}
