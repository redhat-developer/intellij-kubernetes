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
import org.assertj.swing.core.MouseButton;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author olkornii@redhat.com
 */
public class ClusterConnectedTest extends AbstractKubernetesTest{

    public ClusterConnectedTest(String clusterName, ComponentFixture kubernetesViewTree, RemoteRobot remoteRobot) {
        super(clusterName, kubernetesViewTree, remoteRobot);
    }

    public void checkClusterConnected(){
        assertNotNull(kubernetesViewTree.findText(clusterName));
        kubernetesViewTree.findText(clusterName).doubleClick(MouseButton.LEFT_BUTTON);
        assertNotNull(kubernetesViewTree.findText(NODES));
        hideClusterContent();
    }
}
