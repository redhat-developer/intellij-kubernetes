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
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.EditorsSplittersFixture;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.ActionToolbarMenu;
import org.jboss.tools.intellij.kubernetes.fixtures.menus.RightClickMenu;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Optional;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olkornii@redhat.com
 */
public class CreateAnotherTypeResourceByEditTest extends AbstractKubernetesTest{

    private static final String newResourceName = "newresourcename2";

    public CreateAnotherTypeResourceByEditTest(String clusterName, ComponentFixture kubernetesViewTree, RemoteRobot remoteRobot) {
        super(clusterName, kubernetesViewTree, remoteRobot);
    }

    public void createAnotherTypeResourceByEdit(){
        getNamedResourceInNodes(resourceName).doubleClick();

        EditorsSplittersFixture editorSplitter = robot.find(EditorsSplittersFixture.class);

        setupNewPod();

        ActionToolbarMenu toolbarMenu = robot.find(ActionToolbarMenu.class);
        toolbarMenu.pushToCluster();

        editorSplitter.closeEditor(); // close editor
        hideClusterContent();

        openClusterContent();
        openResourceContentList(new String[] {"Workloads", "Deployments"});
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "New resource was not been created.", () -> isResourceCreated(newResourceName, false));

        //delete pod to refresh label
        Optional<RemoteText> deploymentResource = kubernetesViewTree.findAllText().stream().filter(remoteText -> remoteText.getText().contains(newResourceName)).findFirst();
        assertTrue(deploymentResource.isPresent());
        deploymentResource.get().rightClick();
        RightClickMenu rightClickMenu = robot.find(RightClickMenu.class);
        rightClickMenu.select("Delete");
        robot.find(ComponentFixture.class, byXpath("//div[@text='Yes']")).click();
        hideClusterContent();
    }

    private void setupNewPod(){
        Keyboard myKeyboard = new Keyboard(robot);

        myKeyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
        myKeyboard.backspace();

        // enter text
        myKeyboard.enterText("apiVersion: apps/v1");
        myKeyboard.enter();
        myKeyboard.enterText("kind: Deployment");
        myKeyboard.enter();
        myKeyboard.enterText("metadata:");
        myKeyboard.enter();
        myKeyboard.enterText("name: " + newResourceName);
        myKeyboard.enter();
        myKeyboard.enterText("labels:");
        myKeyboard.enter();
        myKeyboard.enterText("app: nginx");
        myKeyboard.enter();
        myKeyboard.backspace();
        myKeyboard.backspace();
        myKeyboard.enterText("spec:");
        myKeyboard.enter();
        myKeyboard.enterText("replicas: 2");
        myKeyboard.enter();
        myKeyboard.enterText("selector:");
        myKeyboard.enter();
        myKeyboard.enterText("matchLabels:");
        myKeyboard.enter();
        myKeyboard.enterText("app: nginx");
        myKeyboard.enter();
        myKeyboard.backspace();
        myKeyboard.backspace();
        myKeyboard.enterText("template:");
        myKeyboard.enter();
        myKeyboard.enterText("metadata:");
        myKeyboard.enter();
        myKeyboard.enterText("labels:");
        myKeyboard.enter();
        myKeyboard.enterText("app: nginx");
        myKeyboard.enter();
        myKeyboard.backspace();
        myKeyboard.backspace();
        myKeyboard.enterText("spec:");
        myKeyboard.enter();
        myKeyboard.enterText("containers:");
        myKeyboard.enter();
        myKeyboard.enterText("- name: nginx");
        myKeyboard.enter();
        myKeyboard.enterText("image: nginx:1.14.2");
        myKeyboard.enter();
        myKeyboard.enterText("ports:");
        myKeyboard.enter();
        myKeyboard.enterText("- containerPort: 80");
    }

}
