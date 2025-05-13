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
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author olkornii@redhat.com
 */
public abstract class AbstractKubernetesTest {

    protected static final String NODES = "Nodes";
    protected static final String resourceName = "hello-minikube"; // resource created in script
    protected final ComponentFixture kubernetesViewTree;
    protected final RemoteRobot robot;
    protected final String clusterName;

    AbstractKubernetesTest(String clusterName, ComponentFixture kubernetesViewTree, RemoteRobot remoteRobot) {
        this.clusterName = clusterName;
        this.kubernetesViewTree = kubernetesViewTree;
        this.robot = remoteRobot;
    }

    public void openResourceContentList(String[] path){
        for (String resourceToOpen : path){
            List<RemoteText> findings = kubernetesViewTree.findAllText(resourceToOpen);
            if (findings.isEmpty()){
                fail("Can't find resource " + resourceToOpen);
            }
            kubernetesViewTree.findText(resourceToOpen).doubleClick(MouseButton.LEFT_BUTTON); // open resource content
            waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Resource to be available.", this::isResourcesLoaded);
        }
    }

    public boolean isResourcesLoaded(){
        return kubernetesViewTree.findAllText().stream().noneMatch(remoteText -> remoteText.getText().contains("loading..."));
    }

    protected void openClusterContent(){
        kubernetesViewTree.findText(clusterName).doubleClick(MouseButton.LEFT_BUTTON);
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View to be available.", this::isNodesOpened);
    }

    public boolean isNodesOpened(){
        return kubernetesViewTree.findAllText().stream().anyMatch(remoteText -> remoteText.getText().equals(NODES));
    }

    public RemoteText getFirstResourceInNodes(){
        openClusterContent();
        openResourceContentList(new String[]{NODES});
        List<RemoteText> allVisibleElements = kubernetesViewTree.findAllText();
        Optional<RemoteText> resourceText = allVisibleElements.stream().filter(remoteText -> remoteText.getText().equals(NODES)).findFirst();
        assertTrue(resourceText.isPresent());
        return allVisibleElements.get(allVisibleElements.indexOf(resourceText.get())+1);
    }

    /**
     * open a resource under the 'Nodes' item
     * @param resourceName the resource to open
     * @return the remoteText found with that name
     */
    public RemoteText getNamedResourceInNodes(String resourceName){
        openClusterContent();
        openResourceContentList(new String[]{NODES});
        return getResource(resourceName);
    }

    protected RemoteText getResource(String resourceName){
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Resource to be available.", () -> kubernetesViewTree.findAllText().stream().anyMatch(remoteText -> remoteText.getText().startsWith(resourceName)));
        List<RemoteText> allVisibleElements = kubernetesViewTree.findAllText();
        Optional<RemoteText> resourceText = allVisibleElements.stream().filter(remoteText -> remoteText.getText().startsWith(resourceName)).findFirst();
        assertTrue(resourceText.isPresent());
        assertTrue(allVisibleElements.contains(resourceText.get()));
        assertTrue(allVisibleElements.lastIndexOf(resourceText.get()) < allVisibleElements.size());
        return allVisibleElements.get(allVisibleElements.indexOf(resourceText.get()));
    }

    public void hideClusterContent(){
        kubernetesViewTree.findText(clusterName).doubleClick(MouseButton.LEFT_BUTTON);
    }

    public boolean isResourceCreated(String resourceName, boolean hardCompare){
        List<RemoteText> kubernetesToolsText = kubernetesViewTree.findAllText();

        Optional<RemoteText> resourceText;
        if (hardCompare){
            resourceText = kubernetesToolsText.stream().filter(remoteText -> remoteText.getText().equals(resourceName)).findFirst();
        } else {
            resourceText = kubernetesToolsText.stream().filter(remoteText -> remoteText.getText().contains(resourceName)).findFirst();
        }
        return resourceText.isPresent();
    }

    public boolean isResourceDeleted(String resourceName){
        return kubernetesViewTree.findAllText().stream().noneMatch(remoteText -> remoteText.getText().equals(resourceName));
    }

    public void scrollToVisible(String text) {
        Keyboard myKeyboard = new Keyboard(robot);
        myKeyboard.hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_F);
        robot.find(ComponentFixture.class, byXpath("//div[@class='SearchTextArea']")).click();
        myKeyboard.enterText(text);
    }

}
