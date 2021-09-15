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
package org.jboss.tools.intellij.kubernetes.fixtures.mainIdeWindow;

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
@DefaultXpath(by = "EditorsSplitters type", xpath = "//div[@class='EditorsSplitters']")
@FixtureName(name = "Editors Splitters")
public class EditorsSplittersFixture extends CommonContainerFixture {
    public EditorsSplittersFixture(@NotNull RemoteRobot remoteRobot, @NotNull RemoteComponent remoteComponent) {
        super(remoteRobot, remoteComponent);
    }

    public ComponentFixture getEditorTextFixture(String fileName){
        return find(ComponentFixture.class, byXpath("//div[@accessiblename='Editor for " + fileName + "' and @class='EditorComponentImpl']"));
    }

    public void closeEditor(String fileName){
        SingleHeighLabelFixture myLabel = find(SingleHeighLabelFixture.class, byXpath("//div[@accessiblename='" + fileName + "' and @class='SingleHeightLabel']"));
        myLabel.close();
    }
}
