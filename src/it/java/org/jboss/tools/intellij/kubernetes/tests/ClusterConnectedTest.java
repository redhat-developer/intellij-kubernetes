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

import com.intellij.remoterobot.fixtures.ComponentFixture;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olkornii@redhat.com
 */
public class ClusterConnectedTest extends AbstractKubernetesTest{
    public static void checkClusterConnected(ComponentFixture kubernetesViewTree){
        String clusterText = kubernetesViewTree.findAllText().get(0).getText();
        assertTrue(clusterText.contains("minikube"));
    }
}
