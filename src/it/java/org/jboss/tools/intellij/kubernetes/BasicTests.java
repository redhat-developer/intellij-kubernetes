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
import com.redhat.devtools.intellij.commonuitest.utils.project.CreateCloseUtils;
import com.redhat.devtools.intellij.commonuitest.utils.runner.IntelliJVersion;
import com.redhat.devtools.intellij.commonuitest.fixtures.mainidewindow.toolwindowspane.ToolWindowPane;
import org.jboss.tools.intellij.kubernetes.fixtures.mainidewindow.KubernetesToolsFixture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.intellij.remoterobot.stepsProcessing.StepWorkerKt.step;
import static com.intellij.remoterobot.utils.RepeatUtilsKt.waitFor;

import org.jboss.tools.intellij.kubernetes.tests.ClusterConnectedTest;
import org.jboss.tools.intellij.kubernetes.tests.CreateResourceByEditTest;
import org.jboss.tools.intellij.kubernetes.tests.OpenResourceEditorTest;
import org.jboss.tools.intellij.kubernetes.tests.EditResourceTest;
import org.jboss.tools.intellij.kubernetes.tests.CreateAnotherTypeResourceByEditTest;

import java.time.Duration;
import java.util.List;

/**
 * JUnit UI tests for intellij-kubernetes
 *
 * @author olkornii@redhat.com
 */
public class BasicTests {

    private static RemoteRobot robot;
    private static ComponentFixture kubernetesViewTree;

    @BeforeAll
    public static void connect() {
        robot = UITestRunner.runIde(IntelliJVersion.COMMUNITY_V_2024_1, 8580);
        CreateCloseUtils.createEmptyProject(robot, "test-project");
        openKubernetesTab();

        KubernetesToolsFixture kubernetesToolsFixture = robot.find(KubernetesToolsFixture.class, Duration.ofSeconds(5));
        kubernetesViewTree = kubernetesToolsFixture.getKubernetesViewTree();
        waitFor(Duration.ofSeconds(15), Duration.ofSeconds(1), "Kubernetes Tree View is not available.", BasicTests::isKubernetesViewTreeAvailable);
    }

    @AfterAll
    public static void closeIde() {
        UITestRunner.closeIde();
    }

    @Test
    public void checkClusterConnected() {
        step("New Empty Project", () -> ClusterConnectedTest.checkClusterConnected(kubernetesViewTree));
    }

    @Test
    public void openResourceEditor() {
        step("open Resource Editor", () -> OpenResourceEditorTest.checkResourceEditor(robot, kubernetesViewTree));
    }

    @Test
    public void editResource() {
        step("edit Resource", () -> EditResourceTest.editResource(robot, kubernetesViewTree));
    }

    @Test
    public void createResourceByEdit() {
        step("create Resource", () -> CreateResourceByEditTest.createResourceByEdit(robot, kubernetesViewTree));

        step("delete Resource", () -> CreateResourceByEditTest.deleteResource(robot, kubernetesViewTree));
    }

    //    @Test
    public void createAnotherResourceTypeByEdit() {
        step("create another type of Resource", () -> CreateAnotherTypeResourceByEditTest.createAnotherTypeResourceByEdit(robot, kubernetesViewTree));
    }

    private static void openKubernetesTab(){
        final ToolWindowPane toolWindowPane = robot.find(ToolWindowPane.class, Duration.ofSeconds(5));
        toolWindowPane.stripeButton("Kubernetes", false).click();
    }

    private static boolean isKubernetesViewTreeAvailable(){
        List<RemoteText> allText = kubernetesViewTree.findAllText();
        String firstText = allText.get(0).getText();
        return !"Nothing to show".equals(firstText);
    }

}