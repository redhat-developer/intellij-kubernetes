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
package org.jboss.tools.intellij.kubernetes.fixtures.menus;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.data.RemoteComponent;
import com.intellij.remoterobot.fixtures.CommonContainerFixture;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.DefaultXpath;
import com.intellij.remoterobot.fixtures.FixtureName;
import org.jetbrains.annotations.NotNull;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;

/**
 * @author olkornii@redhat.com
 */
@DefaultXpath(by = "ActionToolbar type", xpath = "//div[@myactiongroup=' (null)']")
@FixtureName(name = "Action Toolbar Impl")
public class ActionToolbarMenu extends CommonContainerFixture {
    public ActionToolbarMenu(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    public void PushToCluster(){
        find(ComponentFixture.class, byXpath("//div[@myicon='upload.svg']")).click();
        try {
            Thread.sleep(5000); // sleep for 3 seconds, cluster need some time to create pods
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void LoadFromCluster(){
        find(ComponentFixture.class, byXpath("//div[@myicon='download.svg']")).click();
    }
}

