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
import com.redhat.devtools.intellij.commonuitest.UITestRunner;
import com.redhat.devtools.intellij.commonuitest.fixtures.dialogs.FlatWelcomeFrame;
import com.redhat.devtools.intellij.commonuitest.fixtures.mainidewindow.toolwindowspane.ToolWindowLeftToolbar;
import com.redhat.devtools.intellij.commonuitest.utils.project.CreateCloseUtils;
import com.redhat.devtools.intellij.commonuitest.utils.runner.IntelliJVersion;
import com.redhat.devtools.intellij.commonuitest.utils.testextension.ScreenshotAfterTestFailExtension;
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.KubernetesToolsFixture;

import org.jboss.tools.intellij.kubernetes.tests.ClusterConnectedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

import org.jboss.tools.intellij.kubernetes.tests.CreateResourceByEditTest;
import org.jboss.tools.intellij.kubernetes.tests.OpenResourceEditorTest;
import org.jboss.tools.intellij.kubernetes.tests.EditResourceTest;
import org.jboss.tools.intellij.kubernetes.tests.CreateAnotherTypeResourceByEditTest;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;

/**
 * JUnit UI tests for intellij-kubernetes
 *
 * @author olkornii@redhat.com
 */
@ExtendWith(ScreenshotAfterTestFailExtension.class)
public class BasicTests {

    private static RemoteRobot robot;
    private static ComponentFixture kubernetesViewTree;
    private static final String CLUSTER_NAME = "minikube";

    @BeforeAll
    public static void connect() {
        robot = UITestRunner.runIde(IntelliJVersion.COMMUNITY_V_2023_1, 8580);

        FlatWelcomeFrame flatWelcomeFrame = robot.find(FlatWelcomeFrame.class, Duration.ofSeconds(10));
        flatWelcomeFrame.disableNotifications();
        flatWelcomeFrame.preventTipDialogFromOpening();

        CreateCloseUtils.createEmptyProject(robot, "test-project");
        openKubernetesTab();

        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class, Duration.ofSeconds(5));
        kubernetesViewTree = kubernetesToolsFixture.getKubernetesViewTree();
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", BasicTests::isKubernetesViewTreeAvailable);
    }

    @AfterAll
    public static void closeIde() {
        CreateCloseUtils.closeProject(robot);
        FlatWelcomeFrame flatWelcomeFrame = robot.find(FlatWelcomeFrame.class, Duration.ofSeconds(10));
        flatWelcomeFrame.clearWorkspace();
        UITestRunner.closeIde();
    }

    @Test
    public void checkClusterConnected() {
        step("New Empty Project", () -> new ClusterConnectedTest(CLUSTER_NAME, kubernetesViewTree, robot).checkClusterConnected());
    }

    @Test
    public void openResourceEditor() {
        step("Open Resource Editor", () -> new OpenResourceEditorTest(CLUSTER_NAME, kubernetesViewTree, robot).checkResourceEditor());
    }

    @Test
    public void editResource() {
        step("Edit Resource", () -> new EditResourceTest(CLUSTER_NAME, kubernetesViewTree, robot).editResource());
    }

    @Test
    public void createResourceByEdit() {
        step("Create Resource", () -> new CreateResourceByEditTest(CLUSTER_NAME, kubernetesViewTree, robot).createResourceByEdit());

        step("Delete Resource", () -> new CreateResourceByEditTest(CLUSTER_NAME, kubernetesViewTree, robot).deleteResource());
    }

    @Test
    public void createAnotherResourceTypeByEdit() {
        step("Create another type of Resource", () -> new CreateAnotherTypeResourceByEditTest(CLUSTER_NAME, kubernetesViewTree, robot).createAnotherTypeResourceByEdit());
    }

    private static void openKubernetesTab(){
        ToolWindowLeftToolbar toolWindowToolbar = robot.find(ToolWindowLeftToolbar.class, Duration.ofSeconds(10));
        toolWindowToolbar.clickStripeButton("Kubernetes");
    }

    private static boolean isKubernetesViewTreeAvailable(){
        List<RemoteText> allText = kubernetesViewTree.findAllText();
        String firstText = allText.get(0).getText();
        return !"Nothing to show".equals(firstText);
    }

}