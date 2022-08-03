/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.fixtures.dialogs;

import com.intellij.remoterobot.RemoteRobot;
import com.intellij.remoterobot.data.RemoteComponent;
import com.intellij.remoterobot.fixtures.CommonContainerFixture;
import com.intellij.remoterobot.fixtures.ComponentFixture;
import com.intellij.remoterobot.fixtures.DefaultXpath;
import com.intellij.remoterobot.fixtures.FixtureName;
import org.jetbrains.annotations.NotNull;

import static com.intellij.remoterobot.search.locators.Locators.byXpath;
import static com.redhat.devtools.intellij.commonuitest.utils.constants.ButtonLabels.CLEAR_ALL_LABEL;

/**
 * IDE Fatal Errors dialog fixture
 *
 * @author zcervink@redhat.com
 */
@DefaultXpath(by = "MyDialog type", xpath = "//div[@accessiblename='IDE Fatal Errors' and @class='MyDialog']")
@FixtureName(name = "IDE Fatal Errors Dialog")
public class IdeFatalErrorsDialogFixture extends CommonContainerFixture {
    public IdeFatalErrorsDialogFixture(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    /**
     * Click on the 'Clear all' button
     */
    public void clearAll() {
        button(CLEAR_ALL_LABEL).click();
    }

    public ComponentFixture exceptionDescriptionJTextArea() {
        return find(ComponentFixture.class, byXpath("//div[@class='JTextArea']"));
    }
}