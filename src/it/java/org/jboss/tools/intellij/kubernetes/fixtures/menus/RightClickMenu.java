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
import com.intellij.remoterobot.fixtures.DefaultXpath;
import com.intellij.remoterobot.fixtures.FixtureName;
import org.jetbrains.annotations.NotNull;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;

/**
 * @author olkornii@redhat.com
 */
@DefaultXpath(by = "MyMenu type", xpath = "//div[@class='MyMenu']")
@FixtureName(name = "My Menu")
public class RightClickMenu extends CommonContainerFixture {
    public RightClickMenu(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    public void select(String selectedItem){
        find(RightClickMenu.class, byXpath("//div[@text='" + selectedItem + "']")).click();
    }
}

